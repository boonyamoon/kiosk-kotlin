package com.pos.kiosk.core.session

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's side of the lock. [pinPromptAccepted] decides what the user answers when an app that is not the device
 * owner asks to pin (Android's "pin this app?" prompt).
 */
class FakeDeviceLockPolicy(
    var owner: Boolean = false,
    var refuseRelease: Boolean = false,
    var pinPromptAccepted: Boolean = true,
) : DeviceLockPolicy {
    var allowed = false
        private set
    var system = SystemLockMode.None
    override val isDeviceOwner: Boolean get() = owner
    override val isLockTaskPermitted: Boolean get() = allowed
    override val systemLockMode: SystemLockMode get() = system
    override var isDeviceSecure: Boolean = true
    override var isPinExitLocked: Boolean? = true
    override var isDefaultHome: Boolean = true

    override fun allowLockTask() {
        if (owner) allowed = true
    }

    override fun releaseDeviceOwner() {
        if (refuseRelease) throw SecurityException("refused")
        allowed = false
        owner = false
    }

    /** An Activity whose startLockTask/stopLockTask act on this fake. */
    fun activity(): Activity = mockk(relaxed = true) {
        every { startLockTask() } answers {
            system = when {
                allowed -> SystemLockMode.Locked
                pinPromptAccepted -> SystemLockMode.Pinned
                else -> SystemLockMode.None
            }
        }
        every { stopLockTask() } answers { system = SystemLockMode.None }
    }
}

class LockTaskControllerTest {

    @Test
    fun `a device owner locks silently on every resume`() {
        val policy = FakeDeviceLockPolicy(owner = true)
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))

        controller.lockIfPermitted(activity)

        assertEquals(LockMode.Locked, controller.state.value.mode)
        assertTrue(controller.state.value.deviceOwner)
    }

    @Test
    fun `a Play Store install asks to pin when it starts`() {
        val policy = FakeDeviceLockPolicy()
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))

        controller.lockIfPermitted(activity)

        verify(exactly = 1) { activity.startLockTask() }
        assertEquals(LockMode.Pinned, controller.state.value.mode)
    }

    @Test
    fun `a declined pin prompt is not repeated on every resume`() {
        val policy = FakeDeviceLockPolicy(pinPromptAccepted = false)
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))

        repeat(3) { controller.lockIfPermitted(activity) }

        verify(exactly = 1) { activity.startLockTask() }
        assertEquals(LockMode.NotPinned, controller.state.value.mode)

        // The on-screen warning still offers it.
        policy.pinPromptAccepted = true
        controller.requestLock(activity)
        assertEquals(LockMode.Pinned, controller.state.value.mode)
    }

    @Test
    fun `a pin that was lost is asked for again on the next resume`() {
        val policy = FakeDeviceLockPolicy()
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)

        policy.system = SystemLockMode.None // unpinned with the gesture
        controller.checkSystemLock()
        assertEquals(LockMode.NotPinned, controller.state.value.mode)

        controller.lockIfPermitted(activity)
        verify(exactly = 2) { activity.startLockTask() }
        assertEquals(LockMode.Pinned, controller.state.value.mode)
    }

    @Test
    fun `a pin confirmed after the prompt shows up on the next focus or touch`() {
        val policy = FakeDeviceLockPolicy()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        val pendingPrompt = mockk<Activity>(relaxed = true) // the prompt is still open: nothing pinned yet
        controller.lockIfPermitted(pendingPrompt)
        assertEquals(LockMode.NotPinned, controller.state.value.mode)

        policy.system = SystemLockMode.Pinned
        controller.checkSystemLock()

        assertEquals(LockMode.Pinned, controller.state.value.mode)
    }

    @Test
    fun `nothing to exit when the app is not locked`() {
        val policy = FakeDeviceLockPolicy(pinPromptAccepted = false)
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)

        assertFalse(controller.suspend(activity))
        assertFalse(controller.suspended.value)
    }

    @Test
    fun `a staff exit unpins until the kiosk is back on its first screen, which asks to pin again`() {
        val policy = FakeDeviceLockPolicy()
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)

        assertTrue(controller.suspend(activity))
        assertEquals(LockMode.Suspended, controller.state.value.mode)
        assertEquals(SystemLockMode.None, policy.system)

        // Back from another app: still open while the exit lasts.
        controller.lockIfPermitted(activity)
        verify(exactly = 1) { activity.startLockTask() }

        controller.endSuspension()
        controller.lockIfPermitted(activity) // what MainActivity does when suspended turns false
        verify(exactly = 2) { activity.startLockTask() }
        assertEquals(LockMode.Pinned, controller.state.value.mode)
    }

    @Test
    fun `pinning is unsafe without a screen lock or the unpin PIN`() {
        val policy = FakeDeviceLockPolicy()
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)
        assertFalse(controller.state.value.pinningUnsafe)

        policy.isPinExitLocked = false
        controller.refresh()
        assertTrue(controller.state.value.pinningUnsafe)

        policy.isPinExitLocked = null // can't be read: not reported as unsafe
        policy.isDeviceSecure = false
        controller.refresh()
        assertTrue(controller.state.value.pinningUnsafe)
    }

    @Test
    fun `releasing device owner unlocks for good and doesn't ask to pin straight away`() {
        val policy = FakeDeviceLockPolicy(owner = true)
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)

        assertTrue(controller.releaseDeviceOwner(activity))
        controller.lockIfPermitted(activity)

        assertFalse(policy.owner)
        verify(exactly = 1) { activity.startLockTask() }
        assertEquals(LockTaskState(LockMode.NotPinned, deviceOwner = false, deviceSecure = true, pinExitLocked = true, defaultHome = true), controller.state.value)
    }

    @Test
    fun `a refused release locks the app again`() {
        val policy = FakeDeviceLockPolicy(owner = true, refuseRelease = true)
        val activity = policy.activity()
        val controller = LockTaskController(policy)
        controller.setArmed(true, mockk(relaxed = true))
        controller.lockIfPermitted(activity)

        assertFalse(controller.releaseDeviceOwner(activity))

        assertEquals(LockMode.Locked, controller.state.value.mode)
    }

    @Test
    fun `nothing is locked on the setup screens, and unpairing unlocks`() {
        val policy = FakeDeviceLockPolicy()
        val activity = policy.activity()
        val controller = LockTaskController(policy)

        controller.lockIfPermitted(activity) // still setting up
        verify(exactly = 0) { activity.startLockTask() }

        controller.setArmed(true, activity)
        controller.lockIfPermitted(activity)
        assertEquals(LockMode.Pinned, controller.state.value.mode)

        controller.setArmed(false, activity)
        assertEquals(SystemLockMode.None, policy.system)
        assertEquals(LockMode.NotPinned, controller.state.value.mode)
    }
}
