package io.github.lumkit.tweak.common.feature

data class LineFlashRomPackage(
    val rootDir: String,
    val imagesDir: String,
    val scripts: List<LineFlashScript>,
    val checksums: LineFlashChecksumBundle,
    val packageAntiVersion: Int?,
    val packageSecurityPatch: String?,
)

data class LineFlashScript(
    val name: String,
    val path: String,
    val type: LineFlashScriptType,
    val behavior: LineFlashBehavior,
    val steps: List<LineFlashStep>,
    val rawLines: List<String>,
    val expectedProducts: Set<String>,
)

enum class LineFlashScriptType {
    BAT,
    SH,
}

enum class LineFlashBehavior {
    CLEAN_ALL,
    CLEAN_ALL_AND_LOCK,
    SAVE_USER_DATA,
    CUSTOM,
}

data class LineFlashChecksumBundle(
    val crcByPartition: Map<String, Long>,
    val sparseCrcByPartition: Map<String, Long>,
) {
    val isEmpty: Boolean
        get() = crcByPartition.isEmpty() && sparseCrcByPartition.isEmpty()
}

sealed interface LineFlashStep {
    data class CheckProduct(val products: Set<String>) : LineFlashStep
    data class CheckAntiRollback(val packageVersion: Int) : LineFlashStep
    data class CheckSecurityPatch(val packageLevel: String) : LineFlashStep
    data object FlashChecksumList : LineFlashStep
    data class Erase(val partition: String) : LineFlashStep
    data class Flash(val partition: String, val fileName: String) : LineFlashStep
    data class SetActive(val slot: String) : LineFlashStep
    data object Reboot : LineFlashStep
    data object OemLock : LineFlashStep
    data class Command(val command: String) : LineFlashStep
}

data class LineFlashValidationReport(
    val romPackage: LineFlashRomPackage,
    val missingFiles: List<String>,
    val crcResults: List<LineFlashFileChecksumResult>,
    val securityWarnings: List<String>,
) {
    val isComplete: Boolean
        get() = missingFiles.isEmpty() && crcResults.all { it.matches }
}

data class LineFlashFileChecksumResult(
    val partition: String,
    val fileName: String,
    val expected: Long,
    val actual: Long,
) {
    val matches: Boolean
        get() = expected == actual
}

data class LineFlashProgress(
    val stage: Stage,
    val scriptName: String,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val stepDescription: String,
    val currentBytes: Long,
    val totalBytes: Long,
    val percent: Int,
) {
    enum class Stage {
        PREPARING,
        VALIDATING,
        FLASHING,
        FINISHING,
    }
}

sealed interface LineFlashResult {
    data class Success(
        val scriptName: String,
        val executedSteps: Int,
        val totalSteps: Int,
    ) : LineFlashResult

    data class Failure(
        val scriptName: String,
        val failedStepIndex: Int,
        val failedStep: String,
        val message: String,
        val cause: Throwable? = null,
    ) : LineFlashResult
}

expect object LineFlashRomUtil {
    suspend fun inspectRomPackage(rootDir: String): LineFlashRomPackage

    suspend fun validatePackage(
        romPackage: LineFlashRomPackage,
        script: LineFlashScript,
        verifyCrc: Boolean = true,
        onProgress: ((LineFlashProgress) -> Unit)? = null,
    ): LineFlashValidationReport

    suspend fun flash(
        device: FastbootDevice,
        romPackage: LineFlashRomPackage,
        script: LineFlashScript,
        verifyBeforeFlash: Boolean = true,
        verifyCrc: Boolean = true,
        onProgress: ((LineFlashProgress) -> Unit)? = null,
        onResult: ((LineFlashResult) -> Unit)? = null,
    ): LineFlashResult
}
