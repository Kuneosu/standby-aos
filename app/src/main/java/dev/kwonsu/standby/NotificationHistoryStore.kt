package dev.kwonsu.standby

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 평문 파일을 만들지 않고 앱 전용 Keystore 키로 수신 기록을 암호화한다. */
internal class NotificationHistoryStore(context: Context,
    private val file: File = File(context.filesDir, "notification_history.enc"),
    private val alias: String = "${context.packageName}.notification_history_v1",
) {
    private val atomic = AtomicFile(file)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun load(): NotificationHistory {
        if (!file.exists() && !File(file.path + ".bak").exists()) return NotificationHistory()
        val bytes = atomic.readFully()
        require(bytes.size in 30..8_000_000 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD("standby-notification-history-v1".toByteArray())
        return NotificationHistoryCodec.decode(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
    }

    fun save(history: NotificationHistory) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("standby-notification-history-v1".toByteArray())
        val encrypted = cipher.doFinal(NotificationHistoryCodec.encode(history).toByteArray(Charsets.UTF_8))
        val stream = atomic.startWrite()
        try { stream.write(byteArrayOf(1)); stream.write(cipher.iv); stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}

internal object NotificationHistoryCodec {
    fun encode(history: NotificationHistory): String = JSONObject().put("version", 1)
        .put("seen", JSONArray(history.seenIds)).put("signatures", JSONObject(history.lastSignatures))
        .put("records", JSONArray().apply { history.records.forEach { record ->
            val p = record.payload
            put(JSONObject().put("id", record.id).put("source", record.sourceKey).put("received", record.receivedAt)
                .put("originalAvailable", record.originalAvailable)
                .put("package", p.packageName).put("app", p.appName).put("title", p.title).put("text", p.text)
                .put("posted", p.postedAt).put("privacy", p.privacy.name).put("rule", p.rule.name)
                .put("publicTitle", p.publicTitle ?: JSONObject.NULL).put("publicText", p.publicText ?: JSONObject.NULL)
                .put("group", p.groupKey ?: JSONObject.NULL).put("summary", p.groupSummary))
        } }).toString()

    fun decode(raw: String): NotificationHistory {
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val records = root.getJSONArray("records")
        val seen = root.getJSONArray("seen")
        val signatures = root.getJSONObject("signatures")
        fun JSONObject.optional(name: String) = if (isNull(name)) null else getString(name)
        return NotificationHistory((0 until minOf(records.length(), NotificationHistory.MAX_RECORDS)).map { index ->
            val r = records.getJSONObject(index)
            val p = NotificationPayload(r.getString("source"), r.getString("package"), r.getString("app").take(80),
                r.getString("title").take(160), r.getString("text").take(600), r.getLong("posted"),
                NotificationPrivacy.valueOf(r.getString("privacy")), r.optional("publicTitle")?.take(160), r.optional("publicText")?.take(600),
                r.optional("group"), r.getBoolean("summary"), NotificationRule.valueOf(r.getString("rule")))
            NotificationRecord(r.getString("id"), r.getString("source"), p, r.getLong("received"), r.getBoolean("originalAvailable"))
        }, (maxOf(0, seen.length() - NotificationHistory.MAX_SEEN) until seen.length()).map { seen.getString(it) },
            signatures.keys().asSequence().take(1000).associateWith { signatures.getString(it) })
    }
}
