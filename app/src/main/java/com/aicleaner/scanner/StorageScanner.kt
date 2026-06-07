package com.aicleaner.scanner

import android.util.Log
import com.aicleaner.engine.ShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans /sdcard storage and categorizes files.
 * Uses direct file system access (no shell commands for speed).
 */
class StorageScanner(private val shell: ShellEngine) {

    companion object {
        private const val TAG = "StorageScanner"
        private const val SDCARD_PATH = "/sdcard"

        // Directories to skip during scan
        private val SKIP_DIRS = setOf(
            "Android",     // System-managed
            ".android",    // System cache
            ".thumbnails", // System thumbnails
            "LOST.DIR"     // System recovery
        )
    }

    /**
     * Scan storage and return categorized results.
     */
    suspend fun scan(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val files = mutableListOf<FileEntry>()
        val sdcard = File(SDCARD_PATH)

        if (!sdcard.exists() || !sdcard.canRead()) {
            Log.e(TAG, "Cannot read /sdcard")
            return@withContext emptyScanResult()
        }

        // Phase 1: Walk filesystem
        onProgress("Scanning files...", 0.1f)
        walkDirectory(sdcard, files, 0)

        // Phase 2: Categorize
        onProgress("Categorizing...", 0.5f)
        val categories = categorizeFiles(files)

        // Phase 3: Analyze findings
        onProgress("Analyzing...", 0.7f)
        val analyzedCategories = analyzeFindings(categories)

        // Phase 4: Generate suggestions
        onProgress("Generating suggestions...", 0.9f)
        val suggestions = generateSuggestions(analyzedCategories)

        // Phase 5: Calculate health
        val health = calculateHealth(analyzedCategories, suggestions)

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Scan complete: ${files.size} files in ${duration}ms")

        onProgress("Done!", 1.0f)

        ScanResult(
            categories = analyzedCategories,
            totalFiles = files.size,
            totalSize = files.sumOf { it.size },
            scanDuration = duration,
            healthScore = health.score,
            suggestions = suggestions
        )
    }

    /**
     * Walk directory tree recursively.
     */
    private fun walkDirectory(
        dir: File,
        files: MutableList<FileEntry>,
        depth: Int
    ) {
        // Limit depth to prevent infinite recursion
        if (depth > 10) return

        val children = dir.listFiles() ?: return

        for (child in children) {
            // Skip hidden and system directories
            if (child.name.startsWith(".") && child.isDirectory) continue
            if (child.isDirectory && child.name in SKIP_DIRS) continue

            if (child.isFile) {
                val ext = child.extension.lowercase()
                val category = FileCategory.fromExtension(ext)

                files.add(FileEntry(
                    path = child.absolutePath,
                    name = child.name,
                    size = child.length(),
                    category = category,
                    lastModified = child.lastModified(),
                    extension = ext
                ))
            } else if (child.isDirectory) {
                walkDirectory(child, files, depth + 1)
            }
        }
    }

    /**
     * Group files by category.
     */
    private fun categorizeFiles(files: List<FileEntry>): Map<FileCategory, List<FileEntry>> {
        return files.groupBy { it.category }
    }

    /**
     * Analyze each category for interesting findings.
     */
    private fun analyzeFindings(
        categories: Map<FileCategory, List<FileEntry>>
    ): Map<FileCategory, CategoryResult> {
        return categories.mapValues { (category, files) ->
            val findings = when (category) {
                FileCategory.IMAGES -> analyzeImages(files)
                FileCategory.VIDEOS -> analyzeVideos(files)
                FileCategory.DOCUMENTS -> analyzeDocuments(files)
                FileCategory.APK_FILES -> analyzeApks(files)
                FileCategory.TRASH -> analyzeTrash(files)
                FileCategory.OTHERS -> analyzeOthers(files)
            }

            CategoryResult(
                category = category,
                files = files,
                totalSize = files.sumOf { it.size },
                fileCount = files.size,
                findings = findings
            )
        }
    }

    /**
     * Analyze images for screenshots, duplicates, etc.
     */
    private fun analyzeImages(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Count screenshots
        val screenshots = files.filter {
            it.name.lowercase().contains("screenshot") ||
            it.path.lowercase().contains("screenshot") ||
            it.path.lowercase().contains("dcim")
        }
        if (screenshots.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.SCREENSHOT,
                message = "${screenshots.size} screenshots found",
                fileCount = screenshots.size,
                estimatedSpace = screenshots.sumOf { it.size }
            ))
        }

        // Find old images (>1 year)
        val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        val oldImages = files.filter { it.lastModified < oneYearAgo }
        if (oldImages.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.OLD_FILE,
                message = "${oldImages.size} images not viewed in over a year",
                fileCount = oldImages.size,
                estimatedSpace = oldImages.sumOf { it.size }
            ))
        }

        // Find large images (>5MB)
        val largeImages = files.filter { it.size > 5 * 1024 * 1024 }
        if (largeImages.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.LARGE_FILE,
                message = "${largeImages.size} large images (>5 MB)",
                fileCount = largeImages.size,
                estimatedSpace = largeImages.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Analyze videos for large files, old files, etc.
     */
    private fun analyzeVideos(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Find large videos (>500MB)
        val largeVideos = files.filter { it.size > 500 * 1024 * 1024 }
        if (largeVideos.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.LARGE_FILE,
                message = "${largeVideos.size} videos larger than 500 MB",
                fileCount = largeVideos.size,
                estimatedSpace = largeVideos.sumOf { it.size }
            ))
        }

        // Find old videos (>1 year)
        val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        val oldVideos = files.filter { it.lastModified < oneYearAgo }
        if (oldVideos.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.OLD_FILE,
                message = "${oldVideos.size} videos not opened in over a year",
                fileCount = oldVideos.size,
                estimatedSpace = oldVideos.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Analyze documents for old PDFs, etc.
     */
    private fun analyzeDocuments(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Find old PDFs (>2 years)
        val twoYearsAgo = System.currentTimeMillis() - (2L * 365 * 24 * 60 * 60 * 1000)
        val oldPdfs = files.filter {
            it.extension == "pdf" && it.lastModified < twoYearsAgo
        }
        if (oldPdfs.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.OLD_FILE,
                message = "${oldPdfs.size} PDFs not opened in two years",
                fileCount = oldPdfs.size,
                estimatedSpace = oldPdfs.sumOf { it.size }
            ))
        }

        // Detect invoices
        val invoices = files.filter {
            it.name.lowercase().let { name ->
                name.contains("invoice") || name.contains("receipt") || name.contains("faktur")
            }
        }
        if (invoices.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.CLUTTER,
                message = "${invoices.size} invoices detected",
                fileCount = invoices.size,
                estimatedSpace = invoices.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Analyze APK files for installed, old, duplicates.
     */
    private fun analyzeApks(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Find old APKs (>1 year)
        val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        val oldApks = files.filter { it.lastModified < oneYearAgo }
        if (oldApks.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.OLD_FILE,
                message = "${oldApks.size} APKs older than one year",
                fileCount = oldApks.size,
                estimatedSpace = oldApks.sumOf { it.size }
            ))
        }

        // Find duplicate APKs (same name)
        val duplicates = files.groupBy { it.name }
            .filter { it.value.size > 1 }
            .flatMap { it.value.drop(1) }
        if (duplicates.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.DUPLICATE,
                message = "${duplicates.size} duplicate APKs",
                fileCount = duplicates.size,
                estimatedSpace = duplicates.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Analyze trash files.
     */
    private fun analyzeTrash(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        if (files.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.CLUTTER,
                message = "Temporary files and cache leftovers",
                fileCount = files.size,
                estimatedSpace = files.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Analyze other files.
     */
    private fun analyzeOthers(files: List<FileEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Find large files
        val largeFiles = files.filter { it.size > 100 * 1024 * 1024 }
        if (largeFiles.isNotEmpty()) {
            findings.add(Finding(
                type = FindingType.LARGE_FILE,
                message = "${largeFiles.size} large files (>100 MB)",
                fileCount = largeFiles.size,
                estimatedSpace = largeFiles.sumOf { it.size }
            ))
        }

        return findings
    }

    /**
     * Generate AI suggestions based on findings.
     */
    private fun generateSuggestions(
        categories: Map<FileCategory, CategoryResult>
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        // Suggestion: Clean trash
        val trash = categories[FileCategory.TRASH]
        if (trash != null && trash.totalSize > 0) {
            suggestions.add(Suggestion(
                id = "clean_trash",
                title = "Clean Trash",
                description = "Remove temporary files, cache leftovers, and junk",
                emoji = "🗑️",
                estimatedSpace = trash.totalSize,
                fileCount = trash.fileCount,
                safetyLevel = SafetyLevel.SAFE,
                action = SuggestionAction.CLEAN
            ))
        }

        // Suggestion: Review APKs
        val apks = categories[FileCategory.APK_FILES]
        if (apks != null && apks.fileCount > 0) {
            val oldApks = apks.files.filter {
                it.lastModified < System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
            }
            if (oldApks.isNotEmpty()) {
                suggestions.add(Suggestion(
                    id = "review_apks",
                    title = "Review APK Files",
                    description = "Remove old APK files that are no longer needed",
                    emoji = "📦",
                    estimatedSpace = oldApks.sumOf { it.size },
                    fileCount = oldApks.size,
                    safetyLevel = SafetyLevel.MODERATE,
                    action = SuggestionAction.REVIEW
                ))
            }
        }

        // Suggestion: Organize screenshots
        val images = categories[FileCategory.IMAGES]
        if (images != null) {
            val screenshots = images.files.filter {
                it.name.lowercase().contains("screenshot") ||
                it.path.lowercase().contains("screenshot")
            }
            if (screenshots.size > 10) {
                suggestions.add(Suggestion(
                    id = "organize_screenshots",
                    title = "Organize Screenshots",
                    description = "Move screenshots to a dedicated folder",
                    emoji = "📸",
                    estimatedSpace = 0, // No space freed, just organizing
                    fileCount = screenshots.size,
                    safetyLevel = SafetyLevel.SAFE,
                    action = SuggestionAction.ORGANIZE
                ))
            }
        }

        // Suggestion: Organize downloads
        val downloads = categories.values.flatMap { it.files }.filter {
            it.path.lowercase().contains("/download/")
        }
        if (downloads.size > 20) {
            suggestions.add(Suggestion(
                id = "organize_downloads",
                title = "Organize Downloads",
                description = "Categorize files in your Downloads folder",
                emoji = "📥",
                estimatedSpace = 0,
                fileCount = downloads.size,
                safetyLevel = SafetyLevel.SAFE,
                action = SuggestionAction.ORGANIZE
            ))
        }

        // Suggestion: Remove duplicate photos
        if (images != null) {
            val duplicates = findDuplicates(images.files)
            if (duplicates.isNotEmpty()) {
                suggestions.add(Suggestion(
                    id = "remove_duplicates",
                    title = "Remove Duplicate Photos",
                    description = "Found duplicate images taking up space",
                    emoji = "🔄",
                    estimatedSpace = duplicates.sumOf { it.size },
                    fileCount = duplicates.size,
                    safetyLevel = SafetyLevel.MODERATE,
                    action = SuggestionAction.CLEAN
                ))
            }
        }

        return suggestions.sortedByDescending { it.estimatedSpace }
    }

    /**
     * Find duplicate files by name and size.
     */
    private fun findDuplicates(files: List<FileEntry>): List<FileEntry> {
        return files.groupBy { "${it.name}_${it.size}" }
            .filter { it.value.size > 1 }
            .flatMap { it.value.drop(1) } // Keep first, mark rest as duplicates
    }

    /**
     * Calculate storage health score.
     */
    private fun calculateHealth(
        categories: Map<FileCategory, CategoryResult>,
        suggestions: List<Suggestion>
    ): StorageHealth {
        var score = 100
        val factors = mutableListOf<HealthFactor>()

        // Factor 1: Trash amount
        val trashSize = categories[FileCategory.TRASH]?.totalSize ?: 0
        if (trashSize > 100 * 1024 * 1024) { // >100MB
            val impact = minOf(20, (trashSize / (100 * 1024 * 1024)).toInt())
            score -= impact
            factors.add(HealthFactor(
                name = "Trash Files",
                impact = -impact,
                description = "${formatSize(trashSize)} of trash files"
            ))
        }

        // Factor 2: APK clutter
        val apkCount = categories[FileCategory.APK_FILES]?.fileCount ?: 0
        if (apkCount > 3) {
            val impact = minOf(15, apkCount * 2)
            score -= impact
            factors.add(HealthFactor(
                name = "APK Clutter",
                impact = -impact,
                description = "$apkCount APK files found"
            ))
        }

        // Factor 3: Storage usage
        val totalSpace = getTotalSpace()
        val freeSpace = getFreeSpace()
        val usagePercent = if (totalSpace > 0) ((totalSpace - freeSpace) * 100 / totalSpace).toInt() else 0
        if (usagePercent > 80) {
            val impact = minOf(20, (usagePercent - 80))
            score -= impact
            factors.add(HealthFactor(
                name = "Storage Usage",
                impact = -impact,
                description = "$usagePercent% storage used"
            ))
        }

        // Factor 4: Old files
        val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        val oldFiles = categories.values.flatMap { it.files }.filter { it.lastModified < oneYearAgo }
        if (oldFiles.size > 100) {
            val impact = minOf(10, oldFiles.size / 100)
            score -= impact
            factors.add(HealthFactor(
                name = "Old Files",
                impact = -impact,
                description = "${oldFiles.size} files not accessed in over a year"
            ))
        }

        // Clamp score
        score = score.coerceIn(0, 100)

        return StorageHealth(
            score = score,
            totalSpace = totalSpace,
            usedSpace = totalSpace - freeSpace,
            freeSpace = freeSpace,
            factors = factors
        )
    }

    /**
     * Get total storage space.
     */
    private fun getTotalSpace(): Long {
        return try {
            val stat = android.os.StatFs(SDCARD_PATH)
            stat.totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get free storage space.
     */
    private fun getFreeSpace(): Long {
        return try {
            val stat = android.os.StatFs(SDCARD_PATH)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Empty scan result for error cases.
     */
    private fun emptyScanResult(): ScanResult {
        return ScanResult(
            categories = emptyMap(),
            totalFiles = 0,
            totalSize = 0,
            scanDuration = 0,
            healthScore = 0,
            suggestions = emptyList()
        )
    }
}
