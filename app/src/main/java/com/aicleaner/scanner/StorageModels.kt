package com.aicleaner.scanner

/**
 * File categories for storage exploration.
 */
enum class FileCategory(val displayName: String, val emoji: String, val description: String) {
    IMAGES("Images", "📸", "Photos, screenshots, and pictures"),
    VIDEOS("Videos", "🎥", "Video recordings and clips"),
    DOCUMENTS("Documents", "📄", "PDFs, Word files, and text documents"),
    APK_FILES("APK Files", "📦", "Android installation packages"),
    TRASH("Trash", "🗑️", "Temporary files, cache, and junk"),
    OTHERS("Others", "📂", "Other files and folders");

    companion object {
        /**
         * Determine category from file extension.
         */
        fun fromExtension(ext: String): FileCategory {
            return when (ext.lowercase()) {
                // Images
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "heic", "heif" -> IMAGES

                // Videos
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v" -> VIDEOS

                // Documents
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt" -> DOCUMENTS

                // APK
                "apk", "xapk", "apks" -> APK_FILES

                // Trash
                "tmp", "temp", "bak", "cache", "log", "thumb", "thumbs", "ds_store" -> TRASH

                // Others
                else -> OTHERS
            }
        }
    }
}

/**
 * A single file entry from scanning.
 */
data class FileEntry(
    val path: String,
    val name: String,
    val size: Long,
    val category: FileCategory,
    val lastModified: Long,
    val extension: String
)

/**
 * Category scan result.
 */
data class CategoryResult(
    val category: FileCategory,
    val files: List<FileEntry>,
    val totalSize: Long,
    val fileCount: Int,
    val findings: List<Finding>
) {
    val totalSizeFormatted: String get() = formatSize(totalSize)
}

/**
 * Interesting finding about a category.
 */
data class Finding(
    val type: FindingType,
    val message: String,
    val fileCount: Int = 0,
    val estimatedSpace: Long = 0
)

enum class FindingType {
    DUPLICATE,
    OLD_FILE,
    LARGE_FILE,
    UNUSED_FILE,
    SCREENSHOT,
    CLUTTER
}

/**
 * Overall scan result.
 */
data class ScanResult(
    val categories: Map<FileCategory, CategoryResult>,
    val totalFiles: Int,
    val totalSize: Long,
    val scanDuration: Long,
    val healthScore: Int,
    val suggestions: List<Suggestion>
) {
    val totalSizeFormatted: String get() = formatSize(totalSize)
}

/**
 * AI-generated suggestion.
 */
data class Suggestion(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val estimatedSpace: Long,
    val fileCount: Int,
    val safetyLevel: SafetyLevel,
    val action: SuggestionAction
) {
    val estimatedSpaceFormatted: String get() = formatSize(estimatedSpace)
}

enum class SafetyLevel(val label: String, val emoji: String) {
    SAFE("Safe", "🟢"),
    MODERATE("Moderate", "🟡"),
    CAREFUL("Careful", "🟠")
}

enum class SuggestionAction {
    ORGANIZE,
    CLEAN,
    REVIEW,
    MOVE
}

/**
 * Storage health breakdown.
 */
data class StorageHealth(
    val score: Int,
    val totalSpace: Long,
    val usedSpace: Long,
    val freeSpace: Long,
    val factors: List<HealthFactor>
) {
    val totalSpaceFormatted: String get() = formatSize(totalSpace)
    val usedSpaceFormatted: String get() = formatSize(usedSpace)
    val freeSpaceFormatted: String get() = formatSize(freeSpace)
    val usedPercentage: Int get() = if (totalSpace > 0) ((usedSpace * 100) / totalSpace).toInt() else 0
}

data class HealthFactor(
    val name: String,
    val impact: Int, // -100 to +100
    val description: String
)

/**
 * Preview of changes.
 */
data class ChangePreview(
    val title: String,
    val description: String,
    val before: List<PreviewItem>,
    val after: List<PreviewItem>,
    val estimatedSpaceFreed: Long,
    val filesAffected: Int
) {
    val estimatedSpaceFreedFormatted: String get() = formatSize(estimatedSpaceFreed)
}

data class PreviewItem(
    val path: String,
    val action: String, // "keep", "move", "delete", "organize"
    val destination: String? = null
)

/**
 * Format bytes to human readable size.
 */
fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
