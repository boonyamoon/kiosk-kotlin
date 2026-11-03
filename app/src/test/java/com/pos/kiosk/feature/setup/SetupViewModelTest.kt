package com.pos.kiosk.feature.setup

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.domain.model.KioskAccess
import com.pos.kiosk.domain.model.KioskBranch
import com.pos.kiosk.domain.model.StaffSession
import com.pos.kiosk.domain.repository.AuthRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val auth = mockk<AuthRepository>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sign in with empty fields shows a hint and never calls the backend`() = runTest {
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.Login)

        assertTrue(viewModel.state.value.missingCredentials)
        coVerify(exactly = 0) { auth.staffLogin(any(), any()) }

        viewModel.onEvent(SetupEvent.EmailChanged("staff@meedee.co"))
        assertFalse(viewModel.state.value.missingCredentials)
    }

    @Test
    fun `successful sign in moves to branch selection and clears the password`() = runTest {
        val staff = StaffSession("staff-token", "staff@meedee.co")
        coEvery { auth.staffLogin("staff@meedee.co", "secret") } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(listOf(KioskBranch("b-1", "สาขาสยาม", "Mee Dee Cafe")), deniedByPermission = false))
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.EmailChanged("staff@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        val state = viewModel.state.value
        assertEquals(SetupStep.BRANCH, state.step)
        assertEquals("", state.password)
        assertEquals(1, state.branches.size)
    }

    @Test
    fun `tapping a branch only selects it and Continue loads its kiosk terminals`() = runTest {
        val staff = StaffSession("staff-token", "staff@meedee.co")
        val rama9 = KioskBranch("b-1", "พระราม 9", "MeeD Cafe")
        val hq = KioskBranch("b-2", "Headquarters", "Salon")
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(listOf(rama9, hq), deniedByPermission = false))
        coEvery { auth.kioskTerminals(staff, "b-2") } returns AppResult.Success(emptyList())
        val viewModel = SetupViewModel(auth)
        viewModel.onEvent(SetupEvent.EmailChanged("staff@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        assertEquals(null, viewModel.state.value.selectedBranch)
        viewModel.onEvent(SetupEvent.ConfirmBranch)
        assertEquals(SetupStep.BRANCH, viewModel.state.value.step)

        viewModel.onEvent(SetupEvent.SelectBranch(hq))
        assertEquals(SetupStep.BRANCH, viewModel.state.value.step)
        coVerify(exactly = 0) { auth.kioskTerminals(any(), any()) }

        viewModel.onEvent(SetupEvent.ConfirmBranch)
        assertEquals(SetupStep.TERMINAL, viewModel.state.value.step)
        assertEquals(hq, viewModel.state.value.selectedBranch)
    }

    @Test
    fun `a single branch is preselected`() = runTest {
        val staff = StaffSession("staff-token", "staff@meedee.co")
        val only = KioskBranch("b-1", "พระราม 9", "MeeD Cafe")
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(listOf(only), deniedByPermission = false))
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.EmailChanged("staff@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        assertEquals(only, viewModel.state.value.selectedBranch)
    }

    @Test
    fun `account without any store goes to the no-business screen`() = runTest {
        val staff = StaffSession("staff-token", "nobody@meedee.co")
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(emptyList(), deniedByPermission = false))
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.EmailChanged("nobody@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        assertEquals(SetupStep.NO_BUSINESS, viewModel.state.value.step)
        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun `store without kiosk permission goes to the no-permission screen`() = runTest {
        val staff = StaffSession("staff-token", "cashier@meedee.co")
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(emptyList(), deniedByPermission = true))
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.EmailChanged("cashier@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        assertEquals(SetupStep.NO_KIOSK_PERMISSION, viewModel.state.value.step)
    }

    @Test
    fun `sign out returns to the login form keeping the email`() = runTest {
        val staff = StaffSession("staff-token", "nobody@meedee.co")
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Success(staff)
        coEvery { auth.kioskAccess(staff) } returns AppResult.Success(KioskAccess(emptyList(), deniedByPermission = false))
        val viewModel = SetupViewModel(auth)
        viewModel.onEvent(SetupEvent.EmailChanged("nobody@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("secret"))
        viewModel.onEvent(SetupEvent.Login)

        viewModel.onEvent(SetupEvent.SignOut)

        assertEquals(SetupStep.LOGIN, viewModel.state.value.step)
        assertEquals("nobody@meedee.co", viewModel.state.value.email)
        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun `wrong password stays on the login step with an error`() = runTest {
        coEvery { auth.staffLogin(any(), any()) } returns AppResult.Failure(AppError.InvalidCredentials)
        val viewModel = SetupViewModel(auth)

        viewModel.onEvent(SetupEvent.EmailChanged("staff@meedee.co"))
        viewModel.onEvent(SetupEvent.PasswordChanged("wrong"))
        viewModel.onEvent(SetupEvent.Login)

        assertEquals(SetupStep.LOGIN, viewModel.state.value.step)
        assertEquals(AppError.InvalidCredentials, viewModel.state.value.error)
    }
}
