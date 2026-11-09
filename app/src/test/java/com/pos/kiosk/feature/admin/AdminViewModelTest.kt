package com.pos.kiosk.feature.admin

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.R
import com.pos.kiosk.core.printer.PrinterInfo
import com.pos.kiosk.core.printer.builtin.PrinterStatus
import com.pos.kiosk.core.session.FakeDeviceLockPolicy
import com.pos.kiosk.core.session.LockMode
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.core.session.LockTaskState
import com.pos.kiosk.domain.repository.AuthRepository
import com.pos.kiosk.domain.repository.ConfigRepository
import com.pos.kiosk.domain.repository.MenuRepository
import com.pos.kiosk.domain.usecase.PrintReceiptUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class AdminViewModelTest {
    private val auth = mockk<AuthRepository>()
    private val config = mockk<ConfigRepository> { every { config } returns MutableStateFlow(null) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val policy = FakeDeviceLockPolicy(owner = true)
    private val lockTask = LockTaskController(policy).apply { setArmed(true, mockk(relaxed = true)) }
    private val activity = policy.activity()

    private val printReceipt = mockk<PrintReceiptUseCase> { coEvery { currentPrinter() } returns null }

    private fun viewModel() = AdminViewModel(auth, config, mockk<MenuRepository>(), printReceipt, lockTask)

    private fun unlockedViewModel(): AdminViewModel {
        coEvery { auth.unlockAdmin("482913") } returns AppResult.Success("สมชาย ใจดี")
        return viewModel().apply {
            type("482913")
            onEvent(AdminEvent.SubmitPin)
        }
    }

    private fun AdminViewModel.type(digits: String) = digits.forEach { onEvent(AdminEvent.PinDigit(it)) }

    @Test
    fun `a PIN shorter than four digits is never sent`() = runTest {
        val viewModel = viewModel()

        viewModel.type("123")
        viewModel.onEvent(AdminEvent.SubmitPin)

        assertFalse(viewModel.state.value.canSubmitPin)
        coVerify(exactly = 0) { auth.unlockAdmin(any()) }
    }

    @Test
    fun `a staff PIN accepted by the backend opens the menu and is cleared`() = runTest {
        coEvery { auth.unlockAdmin("482913") } returns AppResult.Success("สมชาย ใจดี")
        val viewModel = viewModel()

        viewModel.type("482913")
        viewModel.onEvent(AdminEvent.SubmitPin)

        val state = viewModel.state.value
        assertTrue(state.authenticated)
        assertEquals("สมชาย ใจดี", state.staffName)
        assertEquals("", state.pin)
    }

    @Test
    fun `a refused PIN shows the reason and clears the pad for the next try`() = runTest {
        coEvery { auth.unlockAdmin(any()) } returns AppResult.Failure(AppError.InvalidPin)
        val viewModel = viewModel()

        viewModel.type("1111")
        viewModel.onEvent(AdminEvent.SubmitPin)

        assertFalse(viewModel.state.value.authenticated)
        assertEquals(AppError.InvalidPin, viewModel.state.value.error)
        assertEquals("", viewModel.state.value.pin)

        viewModel.type("2")
        assertNull("typing again clears the error", viewModel.state.value.error)
    }

    @Test
    fun `the pad takes digits only, up to ten, and backspace removes the last one`() = runTest {
        val viewModel = viewModel()

        viewModel.type("12a3456789012")
        assertEquals("1234567890", viewModel.state.value.pin)

        viewModel.onEvent(AdminEvent.PinBackspace)
        assertEquals("123456789", viewModel.state.value.pin)
    }

    @Test
    fun `the lock buttons do nothing until a staff PIN opened the menu`() = runTest {
        lockTask.lockIfPermitted(activity)
        val viewModel = viewModel()

        viewModel.exitLockTask(activity)

        assertEquals(LockMode.Locked, viewModel.state.value.lock.mode)
    }

    @Test
    fun `staff can exit the lock and lock again`() = runTest {
        lockTask.lockIfPermitted(activity)
        val viewModel = unlockedViewModel()

        viewModel.exitLockTask(activity)
        assertEquals(LockMode.Suspended, viewModel.state.value.lock.mode)

        viewModel.lockNow(activity)
        lockTask.lockIfPermitted(activity) // MainActivity, once the exit has ended
        assertEquals(LockMode.Locked, viewModel.state.value.lock.mode)
    }

    @Test
    fun `releasing device owner reports the result`() = runTest {
        lockTask.lockIfPermitted(activity)
        val viewModel = unlockedViewModel()

        viewModel.releaseDeviceOwner(activity)

        assertEquals(LockMode.NotPinned, viewModel.state.value.lock.mode)
        assertFalse(viewModel.state.value.lock.deviceOwner)
        assertEquals(R.string.admin_device_owner_released, viewModel.state.value.messageRes)
    }

    @Test
    fun `the menu shows the device's built-in printer, and its state after a test print`() = runTest {
        val ready = PrinterInfo.BuiltIn("Sunmi", 576, PrinterStatus.Ready)
        val noPaper = ready.copy(status = PrinterStatus.OutOfPaper)
        coEvery { printReceipt.currentPrinter() } returnsMany listOf(ready, noPaper)
        coEvery { printReceipt.printTestPage() } returns AppResult.Failure(AppError.PrinterOutOfPaper)
        val viewModel = unlockedViewModel()
        assertEquals(ready, viewModel.state.value.printer)

        viewModel.onEvent(AdminEvent.TestPrint)

        assertEquals(noPaper, viewModel.state.value.printer)
        assertEquals(AppError.PrinterOutOfPaper, viewModel.state.value.error)
    }

    private val usbPrinter = PrinterInfo.Usb("XP-80C", 576, PrinterStatus.Offline, permitted = false)

    @Test
    fun `the USB prompt can't be reached before a staff PIN opened the menu`() = runTest {
        every { printReceipt.requestUsbPermission() } just Runs
        val viewModel = viewModel()

        viewModel.onEvent(AdminEvent.AllowUsbPrinter)

        verify(exactly = 0) { printReceipt.requestUsbPermission() }
    }

    @Test
    fun `the USB prompt waits for a staff exit, since Android can't show it over a locked app`() = runTest {
        every { printReceipt.requestUsbPermission() } just Runs
        lockTask.lockIfPermitted(activity)
        val viewModel = unlockedViewModel()

        viewModel.onEvent(AdminEvent.AllowUsbPrinter)
        verify(exactly = 0) { printReceipt.requestUsbPermission() }
        assertFalse(viewModel.state.value.canAskForUsbPermission)

        viewModel.exitLockTask(activity)
        assertTrue(viewModel.state.value.canAskForUsbPermission)
        viewModel.onEvent(AdminEvent.AllowUsbPrinter)

        verify(exactly = 1) { printReceipt.requestUsbPermission() }
    }

    @Test
    fun `a pinned app counts as locked for the USB prompt, and an unpinned one does not`() {
        assertFalse(AdminUiState(lock = LockTaskState(LockMode.Pinned)).canAskForUsbPermission)
        assertFalse(AdminUiState(lock = LockTaskState(LockMode.Locked)).canAskForUsbPermission)
        assertTrue(AdminUiState(lock = LockTaskState(LockMode.NotPinned)).canAskForUsbPermission)
        assertTrue(AdminUiState(lock = LockTaskState(LockMode.Suspended)).canAskForUsbPermission)
    }

    @Test
    fun `coming back from the prompt shows the answer`() = runTest {
        coEvery { printReceipt.currentPrinter() } returnsMany listOf(usbPrinter, usbPrinter.copy(status = PrinterStatus.Ready, permitted = true))
        val viewModel = unlockedViewModel()

        viewModel.onEvent(AdminEvent.Resumed)

        assertEquals(true, (viewModel.state.value.printer as PrinterInfo.Usb).permitted)
    }

    @Test
    fun `the paper staff pick is saved and shown`() = runTest {
        coEvery { printReceipt.setUsbPaperWidth(any()) } just Runs
        coEvery { printReceipt.currentPrinter() } returnsMany listOf(usbPrinter, usbPrinter.copy(paperWidthDots = 384))
        val viewModel = unlockedViewModel()

        viewModel.onEvent(AdminEvent.UsbPaperChosen(384))

        coVerify { printReceipt.setUsbPaperWidth(384) }
        assertEquals(384, viewModel.state.value.printer?.paperWidthDots)
    }

    @Test
    fun `the paper can't be changed before a staff PIN opened the menu`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(AdminEvent.UsbPaperChosen(384))

        coVerify(exactly = 0) { printReceipt.setUsbPaperWidth(any()) }
    }
}
