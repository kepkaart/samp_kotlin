package com.armzonrp.mobile

import android.content.Context
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class GameManager private constructor(private val context: Context) {
    interface GameLaunchCallback {
        fun onError(message: String)
        fun showLoading(show: Boolean)
        fun onProgress(progress: Int, message: String)
    }

    interface GameInstallCallback {
        fun onProgress(progress: Int, message: String)
        fun onSuccess()
        fun onError(message: String)
    }

    private var launchCallback: GameLaunchCallback? = null
    private val gameDir = File(context.getExternalFilesDir(null), "gtasa").apply {
        mkdirs()
        setWritable(true)
    }
    private val libDir = File(gameDir, "lib/armeabi-v7a").apply { mkdirs() }

    fun setLaunchCallback(callback: GameLaunchCallback) {
        this.launchCallback = callback
    }

    fun getGameDirectory(): File = gameDir

    fun areGameFilesValid(): Boolean {
        val requiredFiles = listOf(
            "lib/armeabi-v7a/libsamp.so",
            "classes.dex",
            "data/gta3.img",
            "data/gta.dat",
            "data/default.ide"
        )

        return try {
            requiredFiles.all { path ->
                val file = File(gameDir, path)
                val exists = file.exists()
                if (!exists) {
                    Log.e("FileCheck", "Missing file: ${file.absolutePath}")
                }
                exists
            }
        } catch (e: Exception) {
            Log.e("GameManager", "Validation error", e)
            false
        }
    }

    fun installFromArchive(archive: File, callback: GameInstallCallback) {
        try {
            callback.onProgress(0, "Verifying archive...")

            if (!archive.exists() || archive.length() == 0L) {
                throw IOException("Archive file is missing or empty")
            }

            if (!verifyArchiveStructure(archive)) {
                throw IOException("Archive has invalid structure")
            }

            callback.onProgress(10, "Cleaning...")
            cleanInstallationDirectory()

            callback.onProgress(20, "Extracting files...")
            extractWithStructure(archive, callback)

            callback.onProgress(90, "Verifying files...")
            verifyCriticalFiles()

            archive.delete()

            callback.onProgress(100, "Installation complete!")
            callback.onSuccess()
        } catch (e: Exception) {
            cleanInstallationDirectory()
            callback.onError("Installation failed: ${e.localizedMessage}")
        }
    }

    private fun verifyArchiveStructure(archive: File): Boolean {
        return try {
            ZipFile(archive).use { zip ->
                val requiredEntries = listOf(
                    "apk_files/classes.dex",
                    "apk_files/lib/armeabi-v7a/libsamp.so",
                    "obb/data/gta3.img"
                )
                requiredEntries.all { zip.getEntry(it) != null }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanInstallationDirectory() {
        gameDir.listFiles()?.forEach { it.deleteRecursively() }
        gameDir.mkdirs()
        libDir.mkdirs()
    }

    private fun extractWithStructure(archive: File, callback: GameInstallCallback) {
        val libDir = File(gameDir, "lib/armeabi-v7a").apply { mkdirs() }
        val dataDir = File(gameDir, "data").apply { mkdirs() }

        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            val total = entries.size.toFloat()
            var processed = 0

            entries.forEach { entry ->
                when {
                    entry.name.startsWith("apk_files/lib/armeabi-v7a/") -> {
                        val file = File(libDir, entry.name.removePrefix("apk_files/lib/armeabi-v7a/"))
                        extractFile(zip, entry, file)
                    }
                    entry.name.startsWith("obb/data/") -> {
                        val file = File(dataDir, entry.name.removePrefix("obb/data/"))
                        extractFile(zip, entry, file)
                    }
                    entry.name.startsWith("apk_files/") && !entry.isDirectory -> {
                        val file = File(gameDir, entry.name.removePrefix("apk_files/"))
                        extractFile(zip, entry, file)
                    }
                }

                processed++
                callback.onProgress((20 + (processed / total * 70)).toInt(),
                    "Extracting: ${entry.name}")
            }
        }
    }

    private fun extractFile(zip: ZipFile, entry: ZipEntry, target: File) {
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }

        if (target.name.endsWith(".so")) {
            target.setReadable(true)
            target.setExecutable(true)
        }
    }

    private fun verifyCriticalFiles() {
        val requiredFiles = listOf(
            "lib/armeabi-v7a/libsamp.so" to 5_000_000L,
            "classes.dex" to 1_000_000L,
            "data/gta3.img" to 1_000_000_000L
        )

        requiredFiles.forEach { (path, minSize) ->
            val file = File(gameDir, path)
            if (!file.exists() || file.length() < minSize) {
                throw IOException("File $path is missing or too small")
            }
        }
    }

    fun launchGame() {
        // Реализация запуска игры
        launchCallback?.showLoading(false)
    }

    companion object {
        @Volatile private var instance: GameManager? = null

        fun getInstance(context: Context): GameManager =
            instance ?: synchronized(this) {
                instance ?: GameManager(context.applicationContext).also { instance = it }
            }
    }
}