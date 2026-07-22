package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteAppUpdateInfo(
    val versioncode: Int = 0,
    val versionName: String = "",
    val versions: List<RemoteAppVersion> = emptyList()
) {
    fun isValid(): Boolean {
        // 支持 Git Releases 模式（按 versionName 比对）与旧的 versioncode 模式
        return (versioncode > 0 || versionName.isNotBlank()) && versions.isNotEmpty()
    }
}

@Serializable
data class RemoteAppVersion(
    val versionname: String = "",
    val downloadUrl: String = "",
    val downloadPassword: String = "",
    val sections: List<RemoteAppUpdateSection> = emptyList()
)

@Serializable
data class RemoteAppUpdateSection(
    val title: String = "",
    val items: List<String> = emptyList()
)
