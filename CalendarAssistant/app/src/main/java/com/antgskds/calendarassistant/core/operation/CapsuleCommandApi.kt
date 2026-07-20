package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec

interface CapsuleCommandApi {
    fun forceRefresh()
    fun showOcrProgress(title: String, content: String)
    fun showOcrResult(
        title: String,
        content: String,
        durationMs: Long = 8000L,
        actions: List<CapsuleActionSpec> = emptyList()
    )
    fun clearOcrCapsule()
    fun showVoiceTranscription(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearVoiceTranscription()
    fun showTextQuickMemo(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearTextQuickMemo()
    fun showQuickMemoRecording(title: String, content: String = "松开保存")
    fun clearQuickMemoRecording()
    fun showModelLoading(title: String, content: String)
    fun clearModelLoading()
}
