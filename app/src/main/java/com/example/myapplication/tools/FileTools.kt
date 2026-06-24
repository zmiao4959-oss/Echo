package com.example.myapplication.tools

import com.example.myapplication.MyApplication
import java.io.File

/**
 * 文件系统工具：read, write, list, grep。
 * 所有路径限定在应用工作区目录内。
 */
object FileTools {

    private val workspaceDir: File
        get() = MyApplication.instance.filesDir.resolve("workspace")

    private const val MAX_READ_BYTES = 512 * 1024L
    private const val MAX_WRITE_BYTES = 2 * 1024 * 1024L
    private const val DEFAULT_READ_LIMIT = 2000
    private const val MAX_READ_LIMIT = 5000

    fun registerAll() {
        ToolRegistry.register(readTool)
        ToolRegistry.register(writeTool)
        ToolRegistry.register(listTool)
        ToolRegistry.register(grepTool)
    }

    // ── read ──
    private val readTool = ToolDefinition(
        name = "read",
        description = "Read a text file inside the workspace. Supports offset/limit (1-based line numbers).",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "read",
                "description" to "Read file contents under the workspace.",
                "parameters" to mapOf(
                    "type" to "object",
                    "required" to listOf("path"),
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Path relative to workspace"),
                        "offset" to mapOf("type" to "integer", "description" to "Start line (1-based, default 1)"),
                        "limit" to mapOf("type" to "integer", "description" to "Max lines to read (default $DEFAULT_READ_LIMIT, max $MAX_READ_LIMIT)")
                    )
                )
            )
        ),
        requireApproval = false,
        riskLevel = "low",
        tags = listOf("filesystem")
    ) { args ->
        val path = args["path"] as? String ?: return@ToolDefinition "Error: path is required"
        val offset = (args["offset"] as? Number)?.toInt() ?: 1
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, MAX_READ_LIMIT) ?: DEFAULT_READ_LIMIT

        val file = resolvePath(path) ?: return@ToolDefinition "Error: path must stay inside workspace"
        if (!file.exists()) return@ToolDefinition "Error: path not found: $path"
        if (file.isDirectory) return@ToolDefinition "Error: '$path' is a directory. Use the list tool instead."

        if (file.length() > MAX_READ_BYTES && !isProbablyText(file)) {
            return@ToolDefinition "Error: file too large (${file.length()} bytes)"
        }

        try {
            val lines = file.readLines()
            val total = lines.size
            val start = (offset - 1).coerceIn(0, total)
            val end = (start + limit).coerceAtMost(total)
            val chunk = lines.subList(start, end)
            val displayPath = file.relativeTo(workspaceDir).path

            val sb = StringBuilder()
            sb.appendLine("File: $displayPath (lines ${start + 1}-$end of $total)")
            for ((i, line) in chunk.withIndex()) {
                sb.appendLine(line)
            }
            if (end < total) {
                sb.appendLine("... (${total - end} more lines; use offset/limit to continue)")
            }
            sb.toString()
        } catch (e: Exception) {
            "Error reading $path: ${e.message}"
        }
    }

    // ── write ──
    private val writeTool = ToolDefinition(
        name = "write",
        description = "Write or overwrite a text file inside the workspace.",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "write",
                "description" to "Write UTF-8 text to a workspace file (creates parent dirs).",
                "parameters" to mapOf(
                    "type" to "object",
                    "required" to listOf("path", "content"),
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File path under workspace"),
                        "content" to mapOf("type" to "string", "description" to "UTF-8 text content")
                    )
                )
            )
        ),
        requireApproval = true,
        riskLevel = "medium",
        tags = listOf("filesystem")
    ) { args ->
        val path = args["path"] as? String ?: return@ToolDefinition "Error: path is required"
        val content = args["content"] as? String ?: return@ToolDefinition "Error: content is required"

        val encoded = content.toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_WRITE_BYTES) {
            return@ToolDefinition "Error: content too large (${encoded.size} bytes, max $MAX_WRITE_BYTES)"
        }

        val file = resolvePath(path) ?: return@ToolDefinition "Error: path must stay inside workspace"
        if (file.exists() && file.isDirectory) return@ToolDefinition "Error: '$path' is a directory"

        try {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            "Successfully wrote ${encoded.size} bytes to ${file.relativeTo(workspaceDir).path}"
        } catch (e: Exception) {
            "Error writing $path: ${e.message}"
        }
    }

    // ── list ──
    private val listTool = ToolDefinition(
        name = "list",
        description = "List files and directories inside the workspace.",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "list",
                "description" to "List directory entries under the workspace (non-recursive by default).",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Directory path (default: workspace root)"),
                        "pattern" to mapOf("type" to "string", "description" to "Glob pattern for names (default '*')"),
                        "recursive" to mapOf("type" to "boolean", "description" to "If true, list recursively (capped)")
                    )
                )
            )
        ),
        requireApproval = false,
        riskLevel = "low",
        tags = listOf("filesystem")
    ) { args ->
        val path = args["path"] as? String ?: "."
        val pattern = args["pattern"] as? String ?: "*"
        val recursive = args["recursive"] as? Boolean ?: false

        val dir = resolvePath(path) ?: return@ToolDefinition "Error: path must stay inside workspace"
        if (!dir.exists() || !dir.isDirectory) return@ToolDefinition "Error: '$path' is not a directory"

        val entries = if (recursive) dir.walkTopDown().toList() else dir.listFiles()?.toList() ?: emptyList()
        val filtered = entries.filter {
            if (pattern == "*") true
            else it.name.contains(pattern.removePrefix("*").removeSuffix("*"))
        }

        if (filtered.isEmpty()) return@ToolDefinition "Directory: ${dir.relativeTo(workspaceDir).path}\n(empty or no matches)"

        val sb = StringBuilder()
        sb.appendLine("Directory: ${dir.relativeTo(workspaceDir).path}  pattern='$pattern'")
        var shown = 0
        for (entry in filtered) {
            if (shown >= 500) {
                sb.appendLine("... (${filtered.size - shown} more entries omitted)")
                break
            }
            val kind = if (entry.isDirectory) "dir" else "file"
            val size = if (entry.isFile) "  ${entry.length()} bytes" else ""
            sb.appendLine("  [$kind] ${entry.relativeTo(workspaceDir).path}$size")
            shown++
        }
        sb.toString()
    }

    // ── grep ──
    private val grepTool = ToolDefinition(
        name = "grep",
        description = "Search for a regex pattern in workspace text files.",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "grep",
                "description" to "Search file contents under the workspace for a regex pattern.",
                "parameters" to mapOf(
                    "type" to "object",
                    "required" to listOf("pattern"),
                    "properties" to mapOf(
                        "pattern" to mapOf("type" to "string", "description" to "Regex pattern to search for"),
                        "path" to mapOf("type" to "string", "description" to "File or directory (default: workspace root)"),
                        "glob" to mapOf("type" to "string", "description" to "Filter files by glob, e.g. '*.py'"),
                        "ignore_case" to mapOf("type" to "boolean", "description" to "Case-insensitive search (default true)")
                    )
                )
            )
        ),
        requireApproval = false,
        riskLevel = "low",
        tags = listOf("filesystem"),
        timeoutSec = 30
    ) { args ->
        val pattern = args["pattern"] as? String ?: return@ToolDefinition "Error: pattern is required"
        val path = args["path"] as? String ?: "."
        val glob = args["glob"] as? String ?: ""
        val ignoreCase = args["ignore_case"] as? Boolean ?: true

        val regex = try {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        } catch (e: Exception) {
            return@ToolDefinition "Error: invalid regex pattern: ${e.message}"
        }

        val target = resolvePath(path) ?: return@ToolDefinition "Error: path must stay inside workspace"
        if (!target.exists()) return@ToolDefinition "Error: path not found: $path"

        val skipDirs = setOf(".git", "__pycache__", "node_modules", ".venv", "venv")

        val files = mutableListOf<File>()
        if (target.isFile) {
            files.add(target)
        } else {
            val walk = target.walkTopDown()
            for (f in walk) {
                if (files.size >= 200) break
                if (!f.isFile) continue
                if (f.parentFile?.name in skipDirs) continue
                if (f.length() > MAX_READ_BYTES) continue
                if (glob.isNotEmpty() && !f.name.contains(glob.removePrefix("*").removeSuffix("*"))) continue
                files.add(f)
            }
        }

        val sb = StringBuilder()
        var totalMatches = 0
        var filesScanned = 0
        for (file in files) {
            filesScanned++
            try {
                val lines = file.readLines()
                for ((lineNo, line) in lines.withIndex()) {
                    if (regex.containsMatchIn(line)) {
                        val rel = file.relativeTo(workspaceDir).path
                        sb.appendLine("$rel:${lineNo + 1}: $line")
                        totalMatches++
                        if (totalMatches >= 80) break
                    }
                }
            } catch (_: Exception) { }
            if (totalMatches >= 80) break
        }

        val header = "grep pattern='$pattern' path='$path' files_scanned=$filesScanned matches=$totalMatches"
        if (totalMatches == 0) "$header\n(no matches)"
        else {
            if (totalMatches >= 80) sb.appendLine("... (stopped at 80 matches)")
            "$header\n$sb"
        }
    }

    // ── helpers ──

    private fun resolvePath(path: String): File? {
        workspaceDir.mkdirs()
        val raw = java.io.File(path.trim())
        val resolved = if (raw.isAbsolute) raw.canonicalFile else workspaceDir.resolve(path).canonicalFile
        return if (resolved.canonicalPath.startsWith(workspaceDir.canonicalPath)) resolved else null
    }

    private fun isProbablyText(file: File): Boolean {
        val textSuffixes = setOf(".txt", ".md", ".py", ".js", ".ts", ".json", ".yaml", ".yml",
            ".toml", ".ini", ".xml", ".html", ".css", ".sql", ".sh", ".bat", ".csv", ".log", ".kt")
        return file.extension.lowercase() in textSuffixes || file.extension.isEmpty()
    }
}
