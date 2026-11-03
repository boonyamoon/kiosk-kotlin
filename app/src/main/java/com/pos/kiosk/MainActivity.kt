package com.pos.kiosk

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pos.kiosk.core.session.AvailabilityPoller
import com.pos.kiosk.core.session.IdleTimeoutController
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.navigation.KioskRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var idle: IdleTimeoutController
    @Inject lateinit var lockTask: LockTaskController
    @Inject lateinit var availability: AvailabilityPoller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { KioskRoot(idle, lockTask, availability) }
        // Locks the app on every resume, the moment the kiosk is paired, and again when a staff exit ends on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    combine(lockTask.armed, lockTask.suspended) { armed, suspended -> armed && !suspended }
                        .collect { shouldLock -> if (shouldLock) lockTask.lockIfPermitted(this@MainActivity) }
                }
                // Android's pin prompt and the unpin gesture don't reliably move focus or pause the activity, so a
                // cheap look every couple of seconds keeps the on-screen warning and the admin status honest.
                while (true) {
                    delay(LOCK_POLL_MS)
                    lockTask.checkSystemLock()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
        // Android's "pin this app?" prompt is a window over ours: focus comes back once it is answered.
        lockTask.checkSystemLock()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            idle.onInteraction()
            lockTask.checkSystemLock()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val LOCK_POLL_MS = 2_000L
    }
}
