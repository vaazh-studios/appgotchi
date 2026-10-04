package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets people look around with sample data before connecting a store. In memory only. */
class DemoMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun enable() {
        _enabled.value = true
    }

    fun disable() {
        _enabled.value = false
    }
}

object DemoData {
    val sections: List<StoreSection> = listOf(
        StoreSection(
            Store.AppStore,
            listOf(
                StoreApp(Store.AppStore, "demo-sproutly", "Sproutly", "com.example.sproutly"),
                StoreApp(Store.AppStore, "demo-tidepool", "Tidepool", "com.example.tidepool"),
            ),
            errorMessage = null,
        ),
        StoreSection(
            Store.GooglePlay,
            listOf(
                StoreApp(Store.GooglePlay, "com.example.sproutly", "Sproutly", "com.example.sproutly"),
                StoreApp(Store.GooglePlay, "com.example.inkwell", "Inkwell", "com.example.inkwell"),
            ),
            errorMessage = null,
        ),
    )
}
