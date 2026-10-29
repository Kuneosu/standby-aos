package dev.kwonsu.standby

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class DiagnosticEvent(val time: String, val event: String, val details: String)

object Diagnostics {
    private lateinit var journal: File
    private val mutableEvents = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events = mutableEvents.asStateFlow()

    @Synchronized fun initialize(context: Context) {
        journal = File(context.filesDir, "diagnostics.jsonl")
        mutableEvents.value = runCatching {
            if (!journal.exists()) emptyList() else journal.readLines().takeLast(100).mapNotNull { line ->
                runCatching { JSONObject(line).let { DiagnosticEvent(it.getString("time"), it.getString("event"), it.getString("details")) } }.getOrNull()
            }.reversed()
        }.getOrDefault(emptyList())
    }

    @Synchronized fun record(event: String, details: String) {
        val entry = DiagnosticEvent(Instant.now().toString(), event, details)
        mutableEvents.value = (listOf(entry) + mutableEvents.value).take(100)
        val json = JSONObject().put("time", entry.time).put("event", event).put("details", details).toString()
        Log.i("FoldStandby", json)
        runCatching {
            if (journal.length() > 256_000) journal.writeText(journal.readLines().takeLast(200).joinToString("\n", postfix = "\n"))
            journal.appendText("$json\n")
        }.onFailure { Log.w("FoldStandby", "진단 파일 기록 실패", it) }
    }

    @Synchronized fun export(context: Context): File {
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(folder, "fold-standby-diagnostics.jsonl").apply {
            writeText(if (journal.exists()) journal.readText() else "")
        }
    }
}

object MonitorRuntime {
    val running = MutableStateFlow(false)
    val status = MutableStateFlow("자동 스탠바이 꺼짐")
    var acknowledgedAttempt: String? = null
    val conditions = MutableStateFlow(StandbyConditions())
    var dismissed = false
}
