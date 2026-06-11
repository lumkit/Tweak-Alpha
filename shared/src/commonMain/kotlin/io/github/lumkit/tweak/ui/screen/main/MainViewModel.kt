package io.github.lumkit.tweak.ui.screen.main

import io.github.lumkit.tweak.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: BaseViewModel() {

    private val _selectedTabIndex = MutableStateFlow(1)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()
    val pagerCount = MainPages.entries.size

    fun setSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
    }
}