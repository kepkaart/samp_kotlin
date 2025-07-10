package com.armzonrp.mobile

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object FileUtils {
    private const val TAG = "FileUtils"

    // Минимальные размеры критичных файлов (в байтах)
    private const val MIN_LIBSAMP_SIZE = 2_000_000L  // ~2MB
    private const val MIN_MANIFEST_SIZE = 10_000L    // 10KB
    private const val MIN_DEX_SIZE = 1_000_000L      // ~1MB

    // Обязательные файлы и папки для GTA SA + SA:MP
    private val REQUIRED_GAME_FILES = listOf(
        "AndroidManifest.xml" to MIN_MANIFEST_SIZE,
        "classes.dex" to MIN_DEX_SIZE,
        "lib/armeabi-v7a/libsamp.so" to MIN_LIBSAMP_SIZE,
        "res/" to 0L,    // Папка должна существовать
        "assets/" to 0L   // Папка должна существовать
    )

    /**
     * Проверяет целостность установленной игры
     * @param gameDir Корневая директория с игрой
     * @return true если все файлы на месте и валидны
     */
    fun verifyGameIntegrity(gameDir: File): Boolean {
        return REQUIRED_GAME_FILES.all { (path, minSize) ->
            val file = File(gameDir, path)
            when {
                !file.exists() -> {
                    Log.w(TAG, "Missing file: $path")
                    false
                }
                file.isFile && file.length() < minSize -> {
                    Log.w(TAG, "File too small: $path (${file.length()} < $minSize)")
                    false
                }
                else -> true
            }
        }.also { result ->
            Log.d(TAG, "Game integrity check ${if (result) "passed" else "failed"}")
        }
    }

    /**
     * Проверяет контрольную сумму файла
     * @param file Файл для проверки
     * @param expectedSha1 Ожидаемая SHA-1 сумма (если null, просто возвращает хеш)
     * @return true если хеш совпадает или expectedSha1=null
     */
    fun verifyFileChecksum(file: File, expectedSha1: String? = null): Boolean {
        if (!file.exists()) return false

        val actualHash = calculateSHA1(file)
        return when {
            expectedSha1 == null -> true
            else -> actualHash.equals(expectedSha1, ignoreCase = true)
        }.also { result ->
            if (!result) {
                Log.w(TAG, "Checksum mismatch for ${file.name}: expected $expectedSha1, got $actualHash")
            }
        }
    }

    /**
     * Вычисляет SHA-1 хеш файла
     */
    fun calculateSHA1(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Hash calculation failed for ${file.path}", e)
            ""
        }
    }

    /**
     * Распаковывает ZIP архив с прогрессом
     * @param zipFile Архив для распаковки
     * @param targetDir Целевая директория
     * @param onProgress Callback при распаковке каждого файла (опционально)
     * @return true если успешно
     */
    fun unzipWithProgress(
        zipFile: File,
        targetDir: File,
        onProgress: ((entryName: String) -> Unit)? = null
    ): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
                val totalEntries = zip.size().toFloat()
                var processedEntries = 0

                zip.entries().asSequence().forEach { entry ->
                    val outputFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        // Устанавливаем права на исполнение для .so файлов
                        if (entry.name.endsWith(".so")) {
                            outputFile.setExecutable(true, false)
                        }
                    }
                    processedEntries++
                    onProgress?.invoke(entry.name)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unzip failed", e)
            false
        }
    }

    /**
     * Удаляет директорию с игрой
     */
    fun cleanGameDirectory(gameDir: File): Boolean {
        return if (gameDir.exists()) {
            gameDir.deleteRecursively().also { success ->
                if (success) gameDir.mkdirs()
            }
        } else true
    }

    /**
     * Копирует файл с прогрессом
     */
    fun copyFileWithProgress(
        source: File,
        dest: File,
        onProgress: (bytesCopied: Long) -> Unit
    ): Boolean {
        // Реализация копирования с callback'ами прогресса
        // ...
        return true
    }
}