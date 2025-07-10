package com.armzonrp.mobile

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ApkUtils {
    private const val TAG = "ApkUtils"
    private const val LIB_SAMP_PATH = "lib/armeabi-v7a/libsamp.so"

    // Обязательные файлы для GTA SA + SA:MP
    private val REQUIRED_FILES = listOf(
        "AndroidManifest.xml" to 10_000L,  // Минимальный размер файла
        "classes.dex" to 1_000_000L,
        "res/" to 0L,  // Папка должна существовать
        "assets/" to 0L,
        LIB_SAMP_PATH to 2_000_000L  // Проверка размера библиотеки
    )

    /**
     * Распаковывает архив с игрой и подменяет библиотеку SA:MP
     * @param zipFile - ZIP архив с декомпилированным APK
     * @param targetDir - Целевая директория для распаковки
     * @param context - Контекст для доступа к assets
     * @return true если операция успешна
     */
    fun installGameFromZip(zipFile: File, targetDir: File, context: Context): Boolean {
        return try {
            // 1. Распаковываем основной архив
            if (!unzipFile(zipFile, targetDir)) {
                Log.e(TAG, "Failed to unzip game files")
                return false
            }

            // 2. Подменяем оригинальную библиотеку на нашу
            replaceSampLibrary(targetDir, context)

            // 3. Проверяем целостность файлов
            validateGameInstallation(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Game installation failed", e)
            false
        }
    }

    /**
     * Распаковывает ZIP файл с прогрессом
     */
    private fun unzipFile(zipFile: File, targetDir: File): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
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
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unzip failed", e)
            false
        }
    }

    /**
     * Заменяет оригинальную библиотеку на нашу из assets
     */
    private fun replaceSampLibrary(gameDir: File, context: Context) {
        val libFile = File(gameDir, LIB_SAMP_PATH).apply {
            parentFile?.mkdirs()
            delete() // Удаляем оригинальную библиотеку если есть
        }

        try {
            // Копируем нашу библиотеку из assets
            context.assets.open("libsamp.so").use { input ->
                FileOutputStream(libFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Устанавливаем права на исполнение
            libFile.setExecutable(true, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace libsamp.so", e)
            throw e
        }
    }

    /**
     * Проверяет наличие всех необходимых файлов
     */
    fun validateGameInstallation(gameDir: File): Boolean {
        return REQUIRED_FILES.all { (path, minSize) ->
            val file = File(gameDir, path)
            when {
                !file.exists() -> {
                    Log.w(TAG, "Missing file: $path")
                    false
                }
                minSize > 0 && file.length() < minSize -> {
                    Log.w(TAG, "File too small: $path (${file.length()} < $minSize)")
                    false
                }
                else -> true
            }
        }.also { result ->
            Log.d(TAG, "Game validation ${if (result) "passed" else "failed"}")
        }
    }

    /**
     * Очищает директорию с игрой
     */
    fun cleanGameDirectory(gameDir: File): Boolean {
        return try {
            if (gameDir.exists()) {
                gameDir.deleteRecursively()
            }
            gameDir.mkdirs()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean game directory", e)
            false
        }
    }
}