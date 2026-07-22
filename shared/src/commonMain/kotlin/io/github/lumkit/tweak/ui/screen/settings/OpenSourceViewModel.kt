package io.github.lumkit.tweak.ui.screen.settings

import androidx.compose.runtime.Immutable
import io.github.lumkit.tweak.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

class OpenSourceViewModel : BaseViewModel() {

    @Immutable
    @Serializable
    data class LicenseBean(
        var title: String,
        var author: String,
        var tip: String,
        var url: String
    )

    private val _licenseState = MutableStateFlow(emptyList<LicenseBean>())
    val licenseState = _licenseState.asStateFlow()

    init {
        _licenseState.value = listOf(
            LicenseBean(
                title = "AndroidX Activity Compose",
                author = "AndroidX",
                tip = "Compose integration with Activity. Version 1.13.0",
                url = "https://github.com/androidx/androidx/tree/androidx-main/activity/activity-compose",
            ),
            LicenseBean(
                title = "AndroidX DataStore",
                author = "AndroidX",
                tip = "Data is stored asynchronously, consistently, and transactionally, overcoming most of the drawbacks of SharedPreferences. Version 1.2.1",
                url = "https://developer.android.com/topic/libraries/architecture/datastore",
            ),
            LicenseBean(
                title = "AndroidX DocumentFile",
                author = "AndroidX",
                tip = "Jetpack document tree API for working with documents and trees through DocumentsContract. Version 1.1.0",
                url = "https://github.com/androidx/androidx/tree/androidx-main/documentfile",
            ),
            LicenseBean(
                title = "AndroidX Lifecycle",
                author = "AndroidX",
                tip = "Lifecycle-aware components perform actions in response to a change in the lifecycle status of another component, such as activities and fragments. Version 2.11.0-rc01",
                url = "https://github.com/androidx/androidx/tree/androidx-main/lifecycle",
            ),
            LicenseBean(
                title = "AndroidX Room",
                author = "AndroidX",
                tip = "The Room persistence library provides an abstraction layer over SQLite to allow for more robust database access while harnessing the full power of SQLite. Version 2.8.4",
                url = "https://github.com/androidx/androidx/tree/androidx-main/room",
            ),
            LicenseBean(
                title = "AndroidX SQLite Bundled",
                author = "AndroidX",
                tip = "AndroidX SQLite APIs with bundled SQLite support for Room and related persistence components. Version 2.7.0",
                url = "https://github.com/androidx/androidx/tree/androidx-main/sqlite",
            ),
            LicenseBean(
                title = "Backdrop",
                author = "kyant0",
                tip = "Compose Multiplatform Liquid Glass effects. Version 2.0.0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass",
            ),
            LicenseBean(
                title = "Coil 3",
                author = "Coil Contributors",
                tip = "An image loading library for Android and Compose Multiplatform. Version 3.5.0",
                url = "https://github.com/coil-kt/coil",
            ),
            LicenseBean(
                title = "Compose Multiplatform",
                author = "JetBrains",
                tip = "A declarative framework for sharing UI code across multiple platforms with Kotlin. Version 1.11.1",
                url = "https://github.com/JetBrains/compose-multiplatform",
            ),
            LicenseBean(
                title = "Kotlin",
                author = "JetBrains",
                tip = "The Kotlin Programming Language. Version 2.4.0",
                url = "https://github.com/JetBrains/kotlin",
            ),
            LicenseBean(
                title = "Kotlinx Serialization",
                author = "JetBrains",
                tip = "Kotlin multiplatform and multi-format reflectionless serialization. Version 1.11.0",
                url = "https://github.com/Kotlin/kotlinx.serialization",
            ),
            LicenseBean(
                title = "kyant Shapes",
                author = "kyant0",
                tip = "iOS-like shapes for Compose Multiplatform. Version 1.2.0",
                url = "https://github.com/Kyant0/Shapes",
            ),
            LicenseBean(
                title = "libsu",
                author = "TopJohnWu",
                tip = "An Android library providing a complete solution for apps using root permissions. Version 6.0.0",
                url = "https://github.com/topjohnwu/libsu",
            ),
            LicenseBean(
                title = "Miuix KMP",
                author = "YuKongA",
                tip = "A UI library for Compose Multiplatform. Version 0.9.3",
                url = "https://github.com/compose-miuix-ui/miuix",
            ),
            LicenseBean(
                title = "Navigation3",
                author = "AndroidX",
                tip = "A new navigation library designed to work with Compose. Version 1.1.1",
                url = "https://developer.android.com/guide/navigation/navigation-3",
            ),
            LicenseBean(
                title = "Reorderable",
                author = "Calvin Lung",
                tip = "Reorder items in Lists and Grids in Jetpack Compose and Compose Multiplatform with drag and drop. Version 3.1.0",
                url = "https://github.com/Calvin-LL/Reorderable",
            ),
            LicenseBean(
                title = "Shizuku API",
                author = "RikkaApps",
                tip = "Shizuku API is the API provided by Shizuku and Sui. With Shizuku API, you can call your Java or JNI code with root or shell identity. Version 13.1.5",
                url = "https://github.com/RikkaApps/Shizuku-API",
            ),
        ).filter {
            it.tip.isNotBlank()
                    && it.url.isNotBlank()
                    && it.title.isNotBlank()
                    && it.author.isNotBlank()
        }.sortedBy { it.title }
    }

}
