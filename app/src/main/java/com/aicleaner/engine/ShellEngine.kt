package com.aicleaner.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ShellEngine — core shell execution layer.
 *
 * Two modes:
 * 1. Direct exec (Runtime.exec) — fast, no PRoot needed for simple commands
 * 2. PRoot exec (via JNI) — full Linux environment for complex operations
 */
open class ShellEngine(private val context: Context) {

    init {
        loadNative()
    }

    companion object {
        private const val TAG = "ShellEngine"

        private var nativeLoaded = false

        fun loadNative() {
            if (!nativeLoaded) {
                try {
                    System.loadLibrary("shell-engine")
                    nativeLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Native library not available: ${e.message}")
                }
            }
        }
    }

    // JNI native methods
    external fun nativeExec(cmd: String): String
    external fun nativeExecProot(prootPath: String, rootfsPath: String, cmd: String): String
    external fun nativeFileExists(path: String): Boolean

    private val appDir = context.filesDir.absolutePath
    val prootPath = "$appDir/proot/proot"
    val rootfsPath = "$appDir/ubuntu"

    /**
     * Execute command directly via Android's shell (no PRoot).
     * Good for simple file operations on /sdcard.
     */
    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$cmd 2>&1"))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()

            val result = if (error.isNotEmpty()) "$output\n[STDERR] $error" else output
            Log.d(TAG, "exec[$cmd] -> ${result.take(200)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}")
            "ERROR: ${e.message}"
        }
    }

    /**
     * Execute command inside PRoot Ubuntu environment.
     * Full Linux tools available (apt, find, du, etc.)
     */
    suspend fun execProot(cmd: String): String = withContext(Dispatchers.IO) {
        if (!isProotReady()) {
            return@withContext "ERROR: PRoot not setup yet. Run setupEnvironment() first."
        }

        Log.d(TAG, "execProot[$cmd]")
        nativeExecProot(prootPath, rootfsPath, cmd)
    }

    /**
     * Smart exec: use direct shell for simple commands, PRoot for complex ones.
     */
    suspend fun smartExec(cmd: String, forceProot: Boolean = false): String {
        return if (forceProot || needsProot(cmd)) {
            execProot(cmd)
        } else {
            exec(cmd)
        }
    }

    /**
     * Check if PRoot + Ubuntu rootfs is set up.
     */
    fun isProotReady(): Boolean {
        val prootFile = File(prootPath)
        val rootfsBin = File(rootfsPath, "bin/bash")
        return prootFile.exists() && prootFile.canExecute() && rootfsBin.exists()
    }

    /**
     * Setup PRoot environment.
     * Downloads PRoot binary and Ubuntu rootfs.
     */
    suspend fun setupEnvironment(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create directories
            onProgress("Creating directories...", 0.05f)
            File(appDir, "proot").mkdirs()
            File(appDir, "ubuntu").mkdirs()

            // Step 2: Download PRoot binary
            val prootFile = File(prootPath)
            if (!prootFile.exists() || !prootFile.canExecute()) {
                onProgress("Downloading PRoot...", 0.15f)
                val prootUrl = "https://github.com/proot-me/proot-me/releases/download/v5.4.0/proot-v5.4.0-aarch64-static"
                downloadFile(prootUrl, prootFile)
                Runtime.getRuntime().exec(arrayOf("chmod", "+x", prootFile.absolutePath)).waitFor()
            }

            // Step 3: Download Ubuntu rootfs
            val rootfsDir = File(rootfsPath)
            val rootfsBin = File(rootfsPath, "bin/bash")
            if (!rootfsBin.exists()) {
                onProgress("Downloading Ubuntu (this may take a few minutes)...", 0.3f)
                val rootfsUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"
                val tarFile = File(appDir, "rootfs.tar.gz")
                downloadFile(rootfsUrl, tarFile)

                onProgress("Extracting Ubuntu...", 0.7f)
                // Extract using Android's tar (available on most devices)
                exec("tar -xzf ${tarFile.absolutePath} -C ${rootfsDir.absolutePath}")
                tarFile.delete()
            }

            // Step 4: Configure rootfs
            onProgress("Configuring environment...", 0.85f)

            // DNS
            val resolvConf = File(rootfsPath, "etc/resolv.conf")
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

            // Link sdcard
            val sdcardLink = File(rootfsPath, "sdcard")
            if (!sdcardLink.exists()) {
                exec("ln -sf /sdcard ${sdcardLink.absolutePath}")
            }

            // Set hostname
            val hostnameFile = File(rootfsPath, "etc/hostname")
            hostnameFile.writeText("aicleaner\n")

            // Step 5: Verify
            onProgress("Verifying setup...", 0.95f)
            val testResult = nativeExecProot(prootPath, rootfsPath, "echo 'PROOT_OK'")
            val success = testResult.contains("PROOT_OK")

            onProgress(if (success) "Setup complete!" else "Setup failed", 1.0f)
            success

        } catch (e: Exception) {
            Log.e(TAG, "Setup failed: ${e.message}")
            onProgress("Setup failed: ${e.message}", 1.0f)
            false
        }
    }

    /**
     * Download a file from URL.
     */
    private fun downloadFile(url: String, target: File) {
        val connection = java.net.URL(url).openConnection()
        connection.connect()

        val input = connection.getInputStream()
        val output = target.outputStream()

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }

        output.flush()
        output.close()
        input.close()
    }

    /**
     * Determine if a command needs PRoot (complex Linux tools).
     */
    private fun needsProot(cmd: String): Boolean {
        val prootCommands = listOf(
            "apt", "dpkg", "pip", "python",
            "awk", "sed", "xargs", "find",
            "sort", "uniq", "wc", "head", "tail"
        )
        val firstWord = cmd.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
        return prootCommands.any { firstWord.startsWith(it) }
    }
}
