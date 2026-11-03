package com.pos.kiosk.core.session

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.pos.kiosk.KioskDeviceAdminReceiver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the admin menu shows about keeping customers inside the app. */
enum class LockMode {
    /** Device owner Lock Task Mode: Home, Recents and the notification shade are off, no prompt. */
    Locked,

    /** Android app pinning (any device, any Play Store install): same blocking, after the user confirms a prompt. */
    Pinned,

    /** Neither: customers can swipe the system bars in and leave the app. A warning shows on screen. */
    NotPinned,

    /** Staff exited the lock from the admin menu; it comes back on its own when the kiosk returns to its first screen. */
    Suspended,
}

data class LockTaskState(
    val mode: LockMode = LockMode.NotPinned,
    /** The app is the device owner, so it can also give that up (the only way to uninstall it without a factory reset). */
    val deviceOwner: Boolean = false,
    /** The device has a screen lock (PIN, pattern, password). Without one, "ask for PIN before unpinning" protects nothing. */
    val deviceSecure: Boolean = false,
    /** Android's "ask for PIN before unpinning"; null when this Android version won't let apps read it. */
    val pinExitLocked: Boolean? = null,
    /** The kiosk is the default Home app, so Home and unlocking always come back to it. */
    val defaultHome: Boolean = false,
) {
    /** Pinned, but a customer who knows the unpin gesture could still get out. */
    val pinningUnsafe: Boolean get() = mode == LockMode.Pinned && (!deviceSecure || pinExitLocked == false)
}

/** What Android reports right now (ActivityManager.getLockTaskModeState). */
enum class SystemLockMode { None, Locked, Pinned }

/** The Android calls the lock needs, behind an interface so the rules are unit-testable. */
interface DeviceLockPolicy {
    val isDeviceOwner: Boolean
    val isLockTaskPermitted: Boolean
    val systemLockMode: SystemLockMode
    val isDeviceSecure: Boolean
    val isPinExitLocked: Boolean?
    val isDefaultHome: Boolean

    /** Allows this package into Lock Task Mode (device owner only). */
    fun allowLockTask()

    /** Clears the lock-task allow list and stops being the device owner. Throws [SecurityException] if Android refuses. */
    fun releaseDeviceOwner()
}

class AndroidDeviceLockPolicy @Inject constructor(@ApplicationContext private val context: Context) : DeviceLockPolicy {
    private val dpm: DevicePolicyManager? = context.getSystemService(DevicePolicyManager::class.java)
    private val activityManager: ActivityManager? = context.getSystemService(ActivityManager::class.java)
    private val keyguard: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private val admin get() = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    override val isDeviceOwner: Boolean get() = dpm?.isDeviceOwnerApp(context.packageName) == true
    override val isLockTaskPermitted: Boolean get() = dpm?.isLockTaskPermitted(context.packageName) == true

    override val systemLockMode: SystemLockMode
        get() = when (activityManager?.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> SystemLockMode.Locked
            ActivityManager.LOCK_TASK_MODE_PINNED -> SystemLockMode.Pinned
            else -> SystemLockMode.None
        }

    override val isDeviceSecure: Boolean get() = keyguard?.isDeviceSecure == true

    // Settings.Secure.LOCK_TO_APP_EXIT_LOCKED is not public API: read it by name, and treat a refusal as "unknown".
    override val isPinExitLocked: Boolean?
        get() = try {
            Settings.Secure.getInt(context.contentResolver, LOCK_TO_APP_EXIT_LOCKED) != 0
        } catch (_: Settings.SettingNotFoundException) {
            false
        } catch (_: SecurityException) {
            null
        }

    override val isDefaultHome: Boolean
        get() {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }

    override fun allowLockTask() {
        dpm?.setLockTaskPackages(admin, arrayOf(context.packageName))
    }

    override fun releaseDeviceOwner() {
        val dpm = dpm ?: return
        dpm.setLockTaskPackages(admin, emptyArray())
        // Deprecated for general use, but still the one way a device owner can hand the device back by itself.
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(context.packageName)
    }

    private companion object {
        const val LOCK_TO_APP_EXIT_LOCKED = "lock_to_app_exit_locked"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceLockModule {
    @Binds abstract fun deviceLockPolicy(impl: AndroidDeviceLockPolicy): DeviceLockPolicy
}

/**
 * Keeps customers inside the app, with the strongest lock the device allows (docs/kiosk-device-setup.md):
 * - device owner → Lock Task Mode, silently, on every resume;
 * - otherwise (the usual Play Store install) → Android app pinning. Android asks the user to confirm every pin, so
 *   the app asks by itself only at moments staff are likely nearby — when the app starts, when a staff exit ends,
 *   and when a pin it had was lost (e.g. someone unpinned it). A declined prompt is not repeated on its own; the
 *   on-screen warning (and the admin menu) offer to pin again.
 *
 * Staff can exit the lock from the admin menu ([suspend]). That exit is temporary: [endSuspension] (called when the
 * kiosk is back on its first screen — admin closed, or the idle timeout sent it home) locks again. MainActivity
 * watches [suspended] while resumed, so the lock returns right away on screen, or as soon as the kiosk is reopened.
 */
@Singleton
class LockTaskController @Inject constructor(private val policy: DeviceLockPolicy) {
    private val _suspended = MutableStateFlow(false)
    val suspended: StateFlow<Boolean> = _suspended.asStateFlow()

    private val _state = MutableStateFlow(LockTaskState())
    val state: StateFlow<LockTaskState> = _state.asStateFlow()

    private val _armed = MutableStateFlow(false)

    /** Locking applies once the kiosk is paired; on the setup screens staff must be free to leave the app. */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    /** Ask for a pin at the next chance: true at start, after a staff exit, and after a pin was lost. */
    private var pinWanted = true

    /** Re-reads Android's state; also notices a pin that was confirmed (or lost) since the last look. */
    fun refresh() {
        val system = policy.systemLockMode
        if (system != SystemLockMode.None) pinWanted = true // if this lock is lost later, ask again
        val mode = when {
            _suspended.value -> LockMode.Suspended
            system == SystemLockMode.Locked -> LockMode.Locked
            system == SystemLockMode.Pinned -> LockMode.Pinned
            else -> LockMode.NotPinned
        }
        _state.value = LockTaskState(
            mode = mode,
            deviceOwner = policy.isDeviceOwner,
            deviceSecure = policy.isDeviceSecure,
            pinExitLocked = policy.isPinExitLocked,
            defaultHome = policy.isDefaultHome,
        )
    }

    /**
     * Cheap check for every touch and focus change: notices a pin confirmed in Android's prompt, or one undone with
     * the unpin gesture (which pauses nothing when no PIN is asked), without re-reading the slower settings.
     */
    fun checkSystemLock() {
        val mode = _state.value.mode
        if (mode == LockMode.Suspended) return
        if ((policy.systemLockMode == SystemLockMode.None) != (mode == LockMode.NotPinned)) refresh()
    }

    /**
     * KioskRoot arms the lock once the kiosk is paired (ordering, out of service, or a load error customers can see)
     * and disarms it on the setup screens (first install, or after unpairing), unlocking so staff can leave the app.
     */
    fun setArmed(armed: Boolean, activity: Activity) {
        if (_armed.value == armed) return
        _armed.value = armed
        if (!armed) {
            _suspended.value = false
            pinWanted = true
            stopLockTask(activity)
        }
        refresh()
    }

    /** Called while MainActivity is resumed and the lock is armed: locks the app unless staff exited the lock. */
    fun lockIfPermitted(activity: Activity) {
        if (_armed.value && !_suspended.value && policy.systemLockMode == SystemLockMode.None) {
            if (policy.isDeviceOwner) policy.allowLockTask()
            when {
                policy.isLockTaskPermitted -> activity.startLockTask() // device owner: no prompt, so always
                pinWanted -> {
                    pinWanted = false // one prompt per occasion, never a loop if it is declined
                    activity.startLockTask()
                }
            }
        }
        refresh()
    }

    /** The on-screen warning or the admin menu asked to pin now; ends a staff exit first if one is on. */
    fun requestLock(activity: Activity) {
        if (_suspended.value) {
            endSuspension() // MainActivity locks as soon as this lands
            return
        }
        if (policy.systemLockMode != SystemLockMode.None) return
        if (policy.isDeviceOwner) policy.allowLockTask()
        activity.startLockTask()
        refresh()
    }

    /**
     * Staff exit from the admin menu. Only a locked or pinned app can be released; returns whether it was.
     * With "ask for PIN before unpinning" on, Android shows its lock screen first: staff unlock the device as usual.
     */
    fun suspend(activity: Activity): Boolean {
        refresh()
        if (_state.value.mode != LockMode.Locked && _state.value.mode != LockMode.Pinned) return false
        _suspended.value = true
        stopLockTask(activity)
        refresh()
        return true
    }

    /** Ends a staff exit. MainActivity locks the app again at once if it is on screen, otherwise when it next resumes. */
    fun endSuspension() {
        if (!_suspended.value) return
        pinWanted = true
        _suspended.value = false
        refresh()
    }

    /** Unlocks the app and stops being the device owner, so the kiosk app can be uninstalled. Returns whether it worked. */
    fun releaseDeviceOwner(activity: Activity): Boolean {
        if (!policy.isDeviceOwner) return false
        stopLockTask(activity)
        val released = try {
            policy.releaseDeviceOwner()
            true
        } catch (_: SecurityException) {
            false
        }
        if (released) {
            pinWanted = false // staff are handing the device back: don't ask to pin it straight away
            _suspended.value = false
            refresh()
        } else {
            lockIfPermitted(activity) // Android refused: lock it again rather than leave it open.
        }
        return released
    }

    private fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: IllegalStateException) {
            // Not in lock task mode.
        }
    }
}
