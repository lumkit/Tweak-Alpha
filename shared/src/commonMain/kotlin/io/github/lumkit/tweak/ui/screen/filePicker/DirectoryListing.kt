package io.github.lumkit.tweak.ui.screen.filePicker

import io.github.lumkit.tweak.common.utils.FileEntry
import io.github.lumkit.tweak.common.utils.Files
import io.github.lumkit.tweak.common.utils.NativeFileResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DirectoryListing(
    private val scope: CoroutineScope,
) {
    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun load(path: String) {
        scope.launch {
            _loading.value = true
            _errorMessage.value = null
            when (val result = Files.listEntries(path)) {
                is NativeFileResult.Success -> {
                    _entries.value = result.value.sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }
                            .thenBy { it.name.lowercase() },
                    )
                }

                is NativeFileResult.Failure -> {
                    _entries.value = emptyList()
                    _errorMessage.value = result.error.message
                }
            }
            _loading.value = false
        }
    }
}
