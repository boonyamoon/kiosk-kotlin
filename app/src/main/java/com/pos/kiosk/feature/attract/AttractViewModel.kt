package com.pos.kiosk.feature.attract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.image.ImagePrefetcher
import com.pos.kiosk.core.session.IdleTimeoutController
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import com.pos.kiosk.domain.usecase.PrintReceiptUseCase
import com.pos.kiosk.domain.usecase.RefreshKioskTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttractUiState(val config: KioskConfig? = null)

@HiltViewModel
class AttractViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val menuRepository: MenuRepository,
    private val session: OrderSessionRepository,
    private val refreshToken: RefreshKioskTokenUseCase,
    idle: IdleTimeoutController,
    prefetcher: ImagePrefetcher,
    private val lockTask: LockTaskController,
    private val printReceipt: PrintReceiptUseCase,
) : ViewModel() {

    val state: StateFlow<AttractUiState> = configRepository.config
        .map { AttractUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttractUiState(configRepository.config.value))

    /** Last touch anywhere in the app; the screensaver starts after enough of it on this screen. */
    val lastInteraction: StateFlow<Long> = idle.lastInteraction

    init {
        // Background, screensaver and banners go to disk as soon as they're known (and again when
        // pos-web changes them), so the screensaver doesn't open on a blank slide or die offline.
        viewModelScope.launch {
            configRepository.config
                .filterNotNull()
                .map { it.display.allImageUrls }
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect(prefetcher::prefetch)
        }
    }

    /** Called every time the attract screen appears: a fresh customer starts here. */
    fun onShown() {
        session.reset()
        // Back on the first screen (admin closed, or the idle timeout sent it home): a staff exit from the
        // screen lock is over, so a forgotten exit never leaves the kiosk open to customers.
        lockTask.endSuspension()
        refreshIfStale()
        // Reach a built-in printer here, not after the first order: binding its service takes a moment, and on old
        // iMin devices Android asks for the USB permission, which belongs in front of staff setting up the kiosk.
        viewModelScope.launch { printReceipt.currentPrinter() }
    }

    /**
     * Config and menu older than a few minutes are reloaded. Also called by the screensaver at the start
     * of every loop, so a kiosk idling on its first screen for hours still picks up pos-web changes.
     */
    fun refreshIfStale() {
        viewModelScope.launch {
            refreshToken()
            if (menuRepository.isStale()) {
                // Independent requests: run them side by side instead of one after another.
                launch { configRepository.refresh() }
                launch { menuRepository.categories(forceRefresh = true) }
                launch { menuRepository.products(forceRefresh = true) }
            }
        }
    }

    fun startOrder(orderType: OrderType) {
        session.setOrderType(orderType)
    }
}
