package com.pos.kiosk.feature.member

import app.cash.turbine.test
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import com.pos.kiosk.domain.model.Member
import com.pos.kiosk.domain.repository.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemberViewModelTest {
    private val members = mockk<MemberRepository>()
    private val session = InMemoryOrderSessionRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun MemberViewModel.type(phone: String) = phone.forEach { onEvent(MemberEvent.Digit(it)) }

    @Test
    fun `found member is attached to the session on continue`() = runTest {
        val member = Member("c-1", "สมชาย ก.", 120)
        coEvery { members.lookup("0812345678") } returns AppResult.Success(member)
        val viewModel = MemberViewModel(members, session)

        viewModel.type("0812345678")
        viewModel.onEvent(MemberEvent.Check)
        assertEquals(member, viewModel.state.value.member)

        viewModel.effects.test {
            viewModel.onEvent(MemberEvent.ContinueAsMember)
            assertEquals(MemberEffect.GoToMenu, awaitItem())
        }
        assertEquals(member, session.session.value.member)
    }

    @Test
    fun `unknown phone shows not found and continues as guest`() = runTest {
        coEvery { members.lookup(any()) } returns AppResult.Success(null)
        val viewModel = MemberViewModel(members, session)

        viewModel.type("0899999999")
        viewModel.onEvent(MemberEvent.Check)
        assertTrue(viewModel.state.value.notFound)

        viewModel.onEvent(MemberEvent.ContinueAsGuest)
        assertNull(session.session.value.member)
    }

    @Test
    fun `phone input is capped at 10 digits and invalid numbers are not looked up`() = runTest {
        val viewModel = MemberViewModel(members, session)

        viewModel.type("123456789012")
        assertEquals("1234567890", viewModel.state.value.phone)
        viewModel.onEvent(MemberEvent.Check)

        assertTrue(viewModel.state.value.invalidPhone)
        assertFalse(viewModel.state.value.loading)
        coVerify(exactly = 0) { members.lookup(any()) }
    }
}
