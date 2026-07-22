package com.antgskds.calendarassistant.core.update

import android.util.Log
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.data.model.RemoteAppUpdateInfo
import com.antgskds.calendarassistant.data.model.RemoteAppUpdateSection
import com.antgskds.calendarassistant.data.model.RemoteAppVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

sealed interface AppUpdateCheckResult {
    data class Success(
        val info: RemoteAppUpdateInfo,
        val hasUpdate: Boolean,
        val localVersionCode: Int
    ) : AppUpdateCheckResult

    data class Error(val message: String) : AppUpdateCheckResult
}

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val REQUEST_TIMEOUT_MS = 15_000L

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    suspend fun check(): AppUpdateCheckResult {
        val url = BuildConfig.APP_UPDATE_URL.trim().removeSurrounding("\"")

        // 未配置更新地址：默认视为已是最新，不再提示「未配置软件更新地址」
        if (url.isBlank()) {
            Log.d(TAG, "APP_UPDATE_URL 为空，跳过检查")
            return AppUpdateCheckResult.Success(
                info = RemoteAppUpdateInfo(versionName = BuildConfig.VERSION_NAME),
                hasUpdate = false,
                localVersionCode = BuildConfig.VERSION_CODE
            )
        }

        return try {
            Log.d(TAG, "开始检查软件更新：localVersionName=${BuildConfig.VERSION_NAME}, url=$url")
            val responseText = client.get(url) {
                // GitHub 公开 API 要求带 User-Agent，否则返回 403
                headers { append(HttpHeaders.UserAgent, "Will-do-App-UpdateChecker") }
            }.bodyAsText()

            val info = when {
                // 自有 Git 仓库的 Releases API（GitHub / Gitee 结构一致）
                url.contains("api.github.com/repos/") || url.contains("gitee.com/api/v5/repos/") ->
                    buildInfoFromRelease(responseText)
                // 兼容旧的「自建 JSON」更新源（直接返回 RemoteAppUpdateInfo）
                else -> json.decodeFromString<RemoteAppUpdateInfo>(responseText)
            }

            if (!info.isValid()) {
                Log.w(TAG, "云端软件更新数据无效")
                AppUpdateCheckResult.Success(
                    info = RemoteAppUpdateInfo(versionName = BuildConfig.VERSION_NAME),
                    hasUpdate = false,
                    localVersionCode = BuildConfig.VERSION_CODE
                )
            } else {
                // 旧版「自建 JSON」更新源：优先按 versioncode 比对，缺失时退化为 versionName 语义比对
                val newer = if (info.versioncode > 0) {
                    info.versioncode > BuildConfig.VERSION_CODE
                } else {
                    isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)
                }
                AppUpdateCheckResult.Success(
                    info = info,
                    hasUpdate = newer,
                    localVersionCode = BuildConfig.VERSION_CODE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查软件更新失败: ${e.javaClass.simpleName}: ${e.message}")
            AppUpdateCheckResult.Error("检查失败，请稍后重试")
        }
    }

    // ── Git Releases API 解析（GitHub / Gitee 通用）──

    @Serializable
    private data class ReleaseAsset(
        val name: String = "",
        val browser_download_url: String = "",
        val url: String = ""
    )

    @Serializable
    private data class ReleaseApiResponse(
        val tag_name: String = "",
        val name: String = "",
        val body: String = "",
        val html_url: String = "",
        val assets: List<ReleaseAsset> = emptyList()
    )

    private fun buildInfoFromRelease(responseText: String): RemoteAppUpdateInfo {
        val resp = runCatching { json.decodeFromString<ReleaseApiResponse>(responseText) }
            .getOrElse { return RemoteAppUpdateInfo(versionName = BuildConfig.VERSION_NAME) }

        val tag = resp.tag_name.ifBlank { resp.name }.ifBlank { BuildConfig.VERSION_NAME }
        val asset = resp.assets.firstOrNull { it.browser_download_url.endsWith(".apk", ignoreCase = true) }
            ?: resp.assets.firstOrNull()
        val downloadUrl = asset?.browser_download_url?.takeIf { it.isNotBlank() } ?: resp.html_url

        val changelogItems = resp.body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val section = RemoteAppUpdateSection(
            title = "更新内容",
            items = changelogItems
        )

        val version = RemoteAppVersion(
            versionname = tag,
            downloadUrl = downloadUrl,
            sections = if (section.items.isNotEmpty()) listOf(section) else emptyList()
        )

        return RemoteAppUpdateInfo(
            versioncode = 0,
            versionName = tag,
            versions = listOf(version)
        )
    }

    // ── 语义化版本号比较 ──

    /**
     * 判断远程版本是否比本地更新。
     * 支持 "2.2.0" / "v2.3.0" / "2.3" 等形式，按 "." 分段逐段比大小。
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = normalizeVersion(remote)
        val l = normalizeVersion(local)
        if (r.isBlank() || l.isBlank()) return false

        val rp = r.split(".").map { it.toIntOrNull() ?: 0 }
        val lp = l.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(rp.size, lp.size)
        for (i in 0 until len) {
            val rv = rp.getOrElse(i) { 0 }
            val lv = lp.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun normalizeVersion(v: String): String {
        return v.trim().removePrefix("v").removePrefix("V").trim()
    }
}
