package com.pos.kiosk.feature.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.repository.MemberRepository
import com.pos.kiosk.domain.repository.OrderSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberUiState(
    val phone: String = "",
    val loading: Boolean = false,
    val member: Member? = null,
    val notFound: Boolean = false,
    val invalidPhone: Boolean = false,
    val error: AppError? = null,
) {
    val canCheck: Boolean get() = phone.length == 10 && !loading
}

sealed interface MemberEvent {
    data class Digit(val digit: Char) : MemberEvent
    data object Backspace : MemberEvent
    data object Check : MemberEvent
    data object ContinueAsMember : MemberEvent
    data object ContinueAsGuest : MemberEvent
}

sealed interface MemberEffect {
    data object GoToMenu : MemberEffect
}

@HiltViewModel
class MemberViewModel @Inject constructor(
    private val members: MemberRepository,
    private val session: OrderSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberUiState())
    val state: StateFlow<MemberUiState> = _state.asStateFlow()

    private val _effects = Channel<MemberEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: MemberEvent) {
        when (event) {
            is MemberEvent.Digit -> _state.update {
                if (it.phone.length >= 10) it else MemberUiState(phone = it.phone + event.digit)
            }
            MemberEvent.Backspace -> _state.update { MemberUiState(phone = it.phone.dropLast(1)) }
            MemberEvent.Check -> check()
            MemberEvent.ContinueAsMember -> proceed(_state.value.member)
            MemberEvent.ContinueAsGuest -> proceed(null)
        }
    }

    private fun check() {
        val phone = _state.value.phone
        if (!PHONE.matches(phone)) {
            _state.update { it.copy(invalidPhone = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notFound = false, member = null) }
            when (val result = members.lookup(phone)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, member = result.data, notFound = result.data == null) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    private fun proceed(member: Member?) {
        session.setMember(member)
        _effects.trySend(MemberEffect.GoToMenu)
    }

    private companion object {
        val PHONE = Regex("^0\\d{9}$")
    }
}
