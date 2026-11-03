package com.pos.kiosk.navigation

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pos.kiosk.R
import com.pos.kiosk.core.session.AvailabilityPoller
import com.pos.kiosk.core.session.IdleTimeoutController
import com.pos.kiosk.core.session.LockTaskController
import com.pos.kiosk.core.session.LocalUserInteraction
import com.pos.kiosk.core.ui.ErrorPanel
import com.pos.kiosk.core.ui.KioskButton
import com.pos.kiosk.core.ui.LoadingPanel
import com.pos.kiosk.core.ui.SoftBackdrop
import com.pos.kiosk.core.ui.theme.KioskTheme
import com.pos.kiosk.core.ui.theme.parseBrandColor
import com.pos.kiosk.feature.admin.AdminRoute
import com.pos.kiosk.feature.admin.LockSetupRoute
import com.pos.kiosk.feature.attract.AttractRoute
import com.pos.kiosk.feature.cart.CartRoute
import com.pos.kiosk.feature.member.MemberRoute
import com.pos.kiosk.feature.menu.MenuRoute
import com.pos.kiosk.feature.payment.PaymentMethodRoute
import com.pos.kiosk.feature.payment.PromptPayRoute
import com.pos.kiosk.feature.setup.SetupRoute
import com.pos.kiosk.feature.success.SuccessRoute
import kotlinx.coroutines.delay

@Composable
fun KioskRoot(
    idle: IdleTimeoutController,
    lockTask: LockTaskController,
    availability: AvailabilityPoller,
    viewModel: KioskRootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // Lock once paired; unlock on the setup screens. Loading (start-up, a retry) leaves it as it was.
    LaunchedEffect(state) {
        val armed = when (state) {
            RootState.NeedsSetup -> false
            RootState.Loading -> return@LaunchedEffect
            else -> true
        }
        activity?.let { lockTask.setArmed(armed, it) }
    }

    KioskTheme(brandColor = parseBrandColor(config?.branch?.themeColor)) {
        // testTagsAsResourceId lets UiAutomator (the baseline profile generator) find the few tagged nodes.
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalUserInteraction provides idle::onInteraction) {
                // Staff setup has its own MeeD-branded look; every customer-facing state sits on
                // the pastel backdrop derived from the branch's theme_color.
                if (state == RootState.NeedsSetup) {
                    SetupRoute(onPaired = viewModel::bootstrap)
                } else {
                    SoftBackdrop(Modifier.fillMaxSize()) {
                        when (val current = state) {
                            RootState.Loading -> LoadingPanel()
                            RootState.NeedsSetup -> Unit
                            RootState.Ready -> Box(Modifier.fillMaxSize()) {
                                OrderNavHost(idle, availability, onUnpaired = viewModel::bootstrap)
                                LockWarning(lockTask, Modifier.align(Alignment.TopEnd).padding(end = 16.dp))
                            }
                            RootState.OutOfService -> OutOfServiceScreen(onRetry = viewModel::bootstrap)
                            RootState.NotEnabled -> OutOfServiceScreen(onRetry = viewModel::bootstrap, detail = R.string.oos_not_enabled)
                            is RootState.Failed -> ErrorPanel(current.error, onRetry = viewModel::bootstrap)
                        }
                    }
                }
            }
        }
    }
}

// Admin and its lock setup guide too: an unlocked admin menu (maybe with the screen lock exited) must not stay
// open on a public kiosk.
private val staffRoutes = listOf(KioskRoute.Admin::class, KioskRoute.LockSetup::class)
private const val OUT_OF_SERVICE_RETRY_MS = 60_000L

private val idleWatchedRoutes =
    listOf(KioskRoute.Member::class, KioskRoute.Menu::class, KioskRoute.Cart::class, KioskRoute.Payment::class) + staffRoutes

@Composable
private fun OrderNavHost(idle: IdleTimeoutController, availability: AvailabilityPoller, onUnpaired: () -> Unit) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val idleEnabled = backStackEntry?.destination?.let { destination -> idleWatchedRoutes.any { destination.hasRoute(it) } } == true
    val onAdmin = backStackEntry?.destination?.let { destination -> staffRoutes.any { destination.hasRoute(it) } } == true

    // Sold-out items on the ordering screens: polled while the kiosk is on screen and a customer is ordering
    // (the idle-watched screens minus the staff ones). Restarting on each screen change also polls at once on
    // arriving at the menu.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val ordering = idleEnabled && !onAdmin
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(ordering && resumed, currentRoute) {
        if (ordering && resumed) availability.run()
    }

    NavHost(navController = nav, startDestination = KioskRoute.Attract) {
        composable<KioskRoute.Attract> {
            AttractRoute(onStart = { nav.navigate(KioskRoute.Member) }, onOpenAdmin = { nav.navigate(KioskRoute.Admin) })
        }
        composable<KioskRoute.Member> {
            MemberRoute(onContinue = { nav.navigate(KioskRoute.Menu) }, onBack = { nav.popBackStack() })
        }
        composable<KioskRoute.Menu> {
            MenuRoute(onCheckout = { nav.navigate(KioskRoute.Cart) }, onBack = { nav.popBackStack() })
        }
        composable<KioskRoute.Cart> {
            CartRoute(onCheckout = { nav.navigate(KioskRoute.Payment) }, onAddMore = { nav.popBackStack(KioskRoute.Menu, inclusive = false) })
        }
        composable<KioskRoute.Payment> {
            PaymentMethodRoute(
                onCashPlaced = { orderId -> nav.navigate(KioskRoute.Success(orderId)) { popUpTo(KioskRoute.Attract) } },
                onPromptPayPlaced = { orderId -> nav.navigate(KioskRoute.PromptPay(orderId)) { popUpTo(KioskRoute.Cart) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable<KioskRoute.PromptPay> {
            PromptPayRoute(
                onCompleted = { orderId -> nav.navigate(KioskRoute.Success(orderId)) { popUpTo(KioskRoute.Attract) } },
                onBackToCart = { nav.popBackStack(KioskRoute.Cart, inclusive = false) },
                onAbandoned = { nav.goHome() },
            )
        }
        composable<KioskRoute.Success> {
            SuccessRoute(onDone = { nav.goHome() })
        }
        composable<KioskRoute.Admin> {
            AdminRoute(onClose = { nav.popBackStack() }, onUnpaired = onUnpaired, onOpenLockSetup = { nav.navigate(KioskRoute.LockSetup) })
        }
        composable<KioskRoute.LockSetup> {
            LockSetupRoute(onClose = { nav.popBackStack() })
        }
    }

    // Attract resets the order session when shown, so going home is all a timeout needs to do.
    // The flow itself is passed (not its value), so touches never recompose this nav host.
    IdleWatcher(
        enabled = idleEnabled,
        lastInteraction = idle.lastInteraction,
        onInteraction = idle::onInteraction,
        onTimeout = { nav.goHome() },
        titleRes = if (onAdmin) R.string.idle_admin_title else R.string.idle_title,
        continueRes = if (onAdmin) R.string.idle_admin_continue else R.string.idle_continue,
    )
}

private fun NavHostController.goHome() = navigate(KioskRoute.Attract) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

/**
 * Customers read the title and message; [detail] tells staff why (e.g. the plan has no kiosk). It also retries by
 * itself every minute, so a kiosk switched back on (plan, override, terminal) returns without anyone touching it.
 */
@Composable
private fun OutOfServiceScreen(onRetry: () -> Unit, @StringRes detail: Int? = null) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(Unit) {
        delay(OUT_OF_SERVICE_RETRY_MS)
        currentOnRetry()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.oos_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.oos_message), style = MaterialTheme.typography.titleLarge)
            detail?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline) }
            KioskButton(stringResource(R.string.action_retry), onRetry, Modifier.width(280.dp))
        }
    }
}
