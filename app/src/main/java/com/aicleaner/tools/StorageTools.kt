package com.aicleaner.tools

import com.aicleaner.engine.ShellEngine
import org.json.JSONObject
import java.nio.file.Paths

/**
 * Validate that a path is under shared storage and normalize it before shell use.
 */
private fun normalizeSharedStoragePath(path: String): String {
    val trimmed = path.trim()
    require(trimmed.isNotBlank()) { "Path is required" }
    require(trimmed.startsWith("/")) { "Access denied: path must be absolute" }
    require(!trimmed.any { it == '\u0000' || it == '\n' || it == '\r' }) {
        "Access denied: path contains unsafe characters"
    }

    val normalized = Paths.get(trimmed).normalize().toString()
    val isSharedStorage =
        normalized == "/sdcard" ||
        normalized.startsWith("/sdcard/") ||
        normalized == "/storage/emulated/0" ||
        normalized.startsWith("/storage/emulated/0/")

    require(isSharedStorage) {
        "Access denied: path must be under /sdcard or /storage/emulated/0"
    }

    val protectedAndroidDir =
        normalized == "/sdcard/Android" ||
        normalized.startsWith("/sdcard/Android/") ||
        normalized == "/storage/emulated/0/Android" ||
        normalized.startsWith("/storage/emulated/0/Android/")

    require(!protectedAndroidDir) {
        "Access denied: Android app-private directories are protected"
    }

    return normalized
}

private fun validateSharedStoragePath(path: String): Result<String> {
    return runCatching { normalizeSharedStoragePath(path) }
}

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

// ============================================================
// list_directory
// ============================================================

class ListDirectoryTool(private val shell: ShellEngine) : Tool(
    name = "list_directory",
    description = "List files and folders in a directory with sizes. Returns file names, sizes, types, and modification dates.",
    parameters = mapOf(
        "path" to ParamDef("string", "Directory path to list (must be under /sdcard)", required = true),
        "max_depth" to ParamDef("integer", "Max depth to scan (default 1, max 3)", required = false)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val path = validateSharedStoragePath(args.getString("path"))
            .getOrElse { return ToolResult(false, "", it.message) }

        val maxDepth = args.optInt("max_depth", 1).coerceIn(1, 3)
        val quotedPath = shellQuote(path)

        val listing = shell.exec("ls -lah $quotedPath 2>/dev/null")
        val sizes = if (maxDepth > 1) {
            shell.exec("du -sh $quotedPath/*/ 2>/dev/null | sort -rh | head -30")
        } else {
            shell.exec("du -sh $quotedPath/* 2>/dev/null | sort -rh | head -30")
        }

        val output = """
            |=== Directory: $path ===
            |$listing
            |
            |=== Sizes ===
            |$sizes
        """.trimMargin()

        return ToolResult(true, output)
    }
}

// ============================================================
// find_files
// ============================================================

class FindFilesTool(private val shell: ShellEngine) : Tool(
    name = "find_files",
    description = "Find files matching criteria. Can filter by name pattern, size, file type, and depth.",
    parameters = mapOf(
        "path" to ParamDef("string", "Directory to search in", required = true),
        "name_pattern" to ParamDef("string", "Filename pattern (*.apk, *.log, etc)", required = false),
        "min_size" to ParamDef("string", "Minimum size (e.g., 10M, 1G)", required = false),
        "max_depth" to ParamDef("integer", "Max directory depth (default 3)", required = false),
        "file_type" to ParamDef("string", "File type: f (file) or d (directory)", required = false)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val path = validateSharedStoragePath(args.getString("path"))
            .getOrElse { return ToolResult(false, "", it.message) }

        val cmd = buildString {
            append("find ${shellQuote(path)}")
            args.optInt("max_depth", 3).coerceIn(1, 5).let { append(" -maxdepth $it") }
            args.optString("file_type").let {
                if (it == "f" || it == "d") append(" -type $it")
            }
            args.optString("name_pattern").let {
                if (it.isNotEmpty()) append(" -name ${shellQuote(it)}")
            }
            args.optString("min_size").let {
                if (it.matches(Regex("""\d+[kKmMgG]?"""))) append(" -size +$it")
            }
            append(" 2>/dev/null | head -50")
        }

        val output = shell.exec(cmd)
        val count = output.lines().filter { it.isNotBlank() }.size

        return ToolResult(
            success = true,
            output = "Found $count files:\n$output",
            metadata = mapOf("count" to count)
        )
    }
}

// ============================================================
// get_file_info
// ============================================================

class GetFileInfoTool(private val shell: ShellEngine) : Tool(
    name = "get_file_info",
    description = "Get detailed info about a file or directory: size, type, permissions, modification date.",
    parameters = mapOf(
        "path" to ParamDef("string", "File or directory path", required = true)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val path = validateSharedStoragePath(args.getString("path"))
            .getOrElse { return ToolResult(false, "", it.message) }
        val quotedPath = shellQuote(path)

        val size = shell.exec("du -sh $quotedPath 2>/dev/null").trim()
        val stat = shell.exec("stat $quotedPath 2>/dev/null")
        val type = shell.exec("file $quotedPath 2>/dev/null").trim()

        return ToolResult(true, "Path: $path\nSize: $size\nType: $type\nDetails:\n$stat")
    }
}

// ============================================================
// delete_file
// ============================================================

class DeleteFileTool(private val shell: ShellEngine) : Tool(
    name = "delete_file",
    description = "Delete a file or directory permanently. Use with caution!",
    parameters = mapOf(
        "path" to ParamDef("string", "Path to delete", required = true),
        "recursive" to ParamDef("boolean", "Delete directories recursively (required for dirs)", required = false)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val path = validateSharedStoragePath(args.getString("path"))
            .getOrElse { return ToolResult(false, "", it.message) }

        // Safety: don't delete /sdcard itself
        if (path == "/sdcard" || path == "/storage/emulated/0") {
            return ToolResult(false, "", "Cannot delete shared storage root!")
        }

        val quotedPath = shellQuote(path)
        val sizeBefore = shell.exec("du -sh $quotedPath 2>/dev/null").trim()
        val recursive = args.optBoolean("recursive", false)

        val cmd = if (recursive) "rm -rf $quotedPath" else "rm -f $quotedPath"
        val result = shell.exec("$cmd 2>&1; if [ -e $quotedPath ]; then echo __BERESIN_DELETE_FAILED__; fi")

        return if (!result.contains("__BERESIN_DELETE_FAILED__") &&
            !result.contains("ERROR:", ignoreCase = true)
        ) {
            ToolResult(true, "Deleted: $path (freed $sizeBefore)")
        } else {
            ToolResult(false, "", "Failed to delete $path: $result")
        }
    }
}

// ============================================================
// move_file
// ============================================================

class MoveFileTool(private val shell: ShellEngine) : Tool(
    name = "move_file",
    description = "Move or rename a file/directory to a new location.",
    parameters = mapOf(
        "source" to ParamDef("string", "Source path", required = true),
        "destination" to ParamDef("string", "Destination path", required = true)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val source = validateSharedStoragePath(args.getString("source"))
            .getOrElse { return ToolResult(false, "", it.message) }
        val destination = validateSharedStoragePath(args.getString("destination"))
            .getOrElse { return ToolResult(false, "", it.message) }

        val quotedSource = shellQuote(source)
        val quotedDestination = shellQuote(destination)

        // Create destination directory if needed
        val destDir = destination.substringBeforeLast("/")
        shell.exec("mkdir -p ${shellQuote(destDir)}")

        val result = shell.exec(
            "mv $quotedSource $quotedDestination 2>&1; " +
                "if [ -e $quotedSource ] || [ ! -e $quotedDestination ]; then echo __BERESIN_MOVE_FAILED__; fi"
        )

        return if (!result.contains("__BERESIN_MOVE_FAILED__") &&
            !result.contains("ERROR:", ignoreCase = true)
        ) {
            ToolResult(true, "Moved: $source → $destination")
        } else {
            ToolResult(false, "", "Failed to move: $result")
        }
    }
}

// ============================================================
// copy_file
// ============================================================

class CopyFileTool(private val shell: ShellEngine) : Tool(
    name = "copy_file",
    description = "Copy a file or directory to a new location.",
    parameters = mapOf(
        "source" to ParamDef("string", "Source path", required = true),
        "destination" to ParamDef("string", "Destination path", required = true),
        "recursive" to ParamDef("boolean", "Copy directories recursively", required = false)
    )
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val source = validateSharedStoragePath(args.getString("source"))
            .getOrElse { return ToolResult(false, "", it.message) }
        val destination = validateSharedStoragePath(args.getString("destination"))
            .getOrElse { return ToolResult(false, "", it.message) }
        val recursive = args.optBoolean("recursive", false)

        val flag = if (recursive) "-r" else ""
        val result = shell.exec(
            "cp $flag ${shellQuote(source)} ${shellQuote(destination)} 2>&1; " +
                "if [ ! -e ${shellQuote(destination)} ]; then echo __BERESIN_COPY_FAILED__; fi"
        )

        return if (!result.contains("__BERESIN_COPY_FAILED__") &&
            !result.contains("ERROR:", ignoreCase = true)
        ) {
            ToolResult(true, "Copied: $source → $destination")
        } else {
            ToolResult(false, "", "Failed to copy: $result")
        }
    }
}

// ============================================================
// get_storage_summary
// ============================================================

class GetStorageSummaryTool(private val shell: ShellEngine) : Tool(
    name = "get_storage_summary",
    description = "Get overall storage usage: total, used, free space, top folders by size, and file type breakdown.",
    parameters = emptyMap()
) {
    override suspend fun execute(args: JSONObject): ToolResult {
        val df = shell.exec("df -h /sdcard 2>/dev/null")
        val topFolders = shell.exec("du -sh /sdcard/*/ 2>/dev/null | sort -rh | head -15")
        val fileTypes = shell.exec("""
            find /sdcard -maxdepth 3 -type f 2>/dev/null |
            sed 's/.*\.//' | sort | uniq -c | sort -rn | head -15
        """.trimIndent())

        return ToolResult(true, """
            |=== Storage Usage ===
            |$df
            |
            |=== Top Folders (by size) ===
            |$topFolders
            |
            |=== File Types ===
            |$fileTypes
        """.trimMargin())
    }
}

// ============================================================
// execute_shell (read-only whitelist)
// ============================================================

class ExecuteShellTool(private val shell: ShellEngine) : Tool(
    name = "execute_shell",
    description = "Execute a read-only shell command. Only safe commands are allowed (ls, find, du, df, cat, stat, wc, head, tail, grep, sort, etc).",
    parameters = mapOf(
        "command" to ParamDef("string", "Shell command to execute", required = true)
    )
) {
    // Whitelist of safe commands (read-only)
    private val SAFE_COMMANDS = setOf(
        "ls", "find", "du", "df", "cat", "stat", "file",
        "wc", "head", "tail", "grep", "awk", "sed", "sort",
        "uniq", "tree", "md5sum", "sha256sum", "basename",
        "dirname", "realpath", "readlink", "which", "echo",
        "date", "whoami", "id", "uname", "env", "printenv"
    )

    // Dangerous operators that could enable injection
    private val DANGEROUS_OPS = listOf(
        ";", "&&", "||", "|", ">", ">>", "<", "`", "$(",
        "${"$"}{", "\n", "\r"
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val command = args.getString("command").trim()

        // Validate: only whitelisted commands
        val firstWord = command.split("\\s+".toRegex()).firstOrNull() ?: ""
        if (firstWord !in SAFE_COMMANDS) {
            return ToolResult(
                false, "",
                "Command not allowed: '$firstWord'. Allowed: ${SAFE_COMMANDS.sorted().joinToString()}"
            )
        }

        // Validate: no destructive operators
        for (op in DANGEROUS_OPS) {
            if (command.contains(op)) {
                return ToolResult(false, "", "Command contains unsafe operator: '$op'")
            }
        }

        // Validate: must operate under /sdcard if path is specified
        if (command.contains("/") && !command.contains("/sdcard")) {
            return ToolResult(false, "", "Commands must operate under /sdcard")
        }

        val output = shell.exec("$command 2>&1")
        return ToolResult(true, output)
    }
}
