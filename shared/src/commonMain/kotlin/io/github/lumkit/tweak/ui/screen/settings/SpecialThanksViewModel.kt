package io.github.lumkit.tweak.ui.screen.settings

import androidx.compose.runtime.Immutable
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.logE
import io.github.lumkit.tweak.common.utils.logI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tweak_alpha.shared.generated.resources.Res

class SpecialThanksViewModel : BaseViewModel() {

    @Immutable
    @Serializable
    data class Contributor(
        val nickname: String = "",
        val contact: String = "",
        val contribution: String = "",
        val avatarUrl: String = "",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _contributors = MutableStateFlow(emptyList<Contributor>())
    val contributors = _contributors.asStateFlow()

    init {
        launch(
            context = Dispatchers.IO,
            block = {
                val content = String(Res.readBytes("files/special_thanks.json"))
                _contributors.value = json.decodeFromString<List<Contributor>>(content)
                    .filter { it.nickname.isNotBlank() }
                    .also {
                        logI("data=$it", TAG)
                    }
            },
            error = {
                logE(it.stackTraceToString(), it, TAG)
            },
        )
    }

    companion object {
        private const val TAG = "SpecialThanksViewModel"
    }
}
