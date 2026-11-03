package com.pos.kiosk.feature.cart

import androidx.lifecycle.ViewModel
import com.pos.kiosk.domain.model.OrderSession
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val session: OrderSessionRepository,
    menu: MenuRepository,
) : ViewModel() {
    val state: StateFlow<OrderSession> = session.session

    /** Kept current by the ~20 s availability poll; a line of one of these is flagged and blocks payment. */
    val unavailableProductIds: StateFlow<Set<String>> = menu.unavailableProductIds

    fun changeQuantity(lineId: String, quantity: Int) = session.updateQuantity(lineId, quantity)

    fun remove(lineId: String) = session.removeItem(lineId)
}
