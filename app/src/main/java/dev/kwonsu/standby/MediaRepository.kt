package dev.kwonsu.standby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** 선택한 파일만 앱 전용 저장소로 복사한다. 원본을 수정하거나 전체 사진 접근 권한을 요청하지 않는다. */
class MediaRepository(private val context: Context) {
    private val directory = File(context.filesDir, "scene_media")
    fun file(name: String?): File? = validMediaName(name)?.let { File(directory, it) }

    suspend fun import(uri: Uri, gif: Boolean): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = "${UUID.randomUUID()}.${if (gif) "gif" else "img"}"
        val target = File(directory, name)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val bytes = ByteArray(8192)
                    var count: Int
                    var total = 0L
                    while (input.read(bytes).also { count = it } != -1) {
                        total += count
                        require(total <= MAX_BYTES) { "파일은 20MB 이하로 선택해 주세요." }
                        output.write(bytes, 0, count)
                    }
                }
            } ?: error("선택한 파일을 열 수 없습니다.")
            if (gif) {
                val header = target.inputStream().use { input -> ByteArray(10).also { require(input.read(it) == 10) { "GIF 파일을 확인해 주세요." } } }
                require(String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) { "GIF 형식의 파일을 선택해 주세요." }
                val width = (header[6].toInt() and 255) or ((header[7].toInt() and 255) shl 8)
                val height = (header[8].toInt() and 255) or ((header[9].toInt() and 255) shl 8)
                require(width > 0 && height > 0 && width.toLong() * height <= 2_100_000) { "GIF는 약 200만 화소 이하로 선택해 주세요." }
                @Suppress("DEPRECATION")
                val movie = Movie.decodeFile(target.path)
                require(movie != null) { "이 GIF는 재생할 수 없습니다. 다른 파일을 선택해 주세요." }
            } else {
                // 가져올 때 손상 여부도 확인하고, 큰 사진은 화면 표시용 크기로만 디코딩한다.
                decodePhoto(target).recycle()
            }
            name
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    suspend fun removeUnused(keep: Set<String>) = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { validMediaName(it.name) != null && it.name !in keep }?.forEach { it.delete() }
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        fun decodePhoto(file: File): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
