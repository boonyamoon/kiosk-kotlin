package com.pos.kiosk.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.pos.kiosk"

// Test tags set in the app (Modifier.testTag, exposed as resource ids by testTagsAsResourceId).
private const val TAG_BRANCH = "branch_option"
private const val TAG_MENU_GRID = "menu_grid"
private const val TAG_PRODUCT = "product_card"
private const val TAG_OPTION_DIALOG = "option_dialog"

private const val SCREEN_TIMEOUT_MS = 20_000L

/** A fixed UI label of the kiosk app in either language (the device locale decides which one is shown). */
private fun label(english: String, thai: String): BySelector =
    By.pkg(TARGET_PACKAGE).text(Pattern.compile("${Pattern.quote(english)}|${Pattern.quote(thai)}"))

/** System UI's one-time "Viewing full screen … Got it" notice, shown the first time the app goes immersive. */
private val immersiveNotice = By.text(Pattern.compile("Got it|เข้าใจแล้ว|รับทราบ"))

private fun MacrobenchmarkScope.dismissImmersiveNotice() {
    device.findObject(immersiveNotice)?.takeIf { it.applicationPackage != TARGET_PACKAGE }?.click()
}

/** Waits for [selector], clearing the immersive-mode notice whenever it pops up over the app. */
private fun MacrobenchmarkScope.awaitObject(selector: BySelector, timeoutMs: Long = SCREEN_TIMEOUT_MS): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        dismissImmersiveNotice()
        if (device.wait(Until.hasObject(selector), 500)) return true
    }
    return false
}

private val attractScreen = label("Eat here", "ทานที่ร้าน")
private val signIn = label("Sign in", "เข้าสู่ระบบ")

/**
 * A fresh install opens on staff setup. Pair it once with the account passed as instrumentation
 * arguments (`kioskEmail`, `kioskPassword`); the pairing survives the process restarts between iterations.
 */
internal fun MacrobenchmarkScope.pairIfNeeded() {
    check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), SCREEN_TIMEOUT_MS)) { "kiosk app did not start" }
    if (awaitObject(attractScreen, timeoutMs = 5_000)) return
    check(awaitObject(signIn)) { "neither the attract screen nor staff setup came up" }

    val args = InstrumentationRegistry.getArguments()
    val email = checkNotNull(args.getString("kioskEmail")) { "pass -Pandroid.testInstrumentationRunnerArguments.kioskEmail=..." }
    val password = checkNotNull(args.getString("kioskPassword")) { "pass -Pandroid.testInstrumentationRunnerArguments.kioskPassword=..." }

    val fields = device.findObjects(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).sortedBy { it.visibleBounds.top }
    check(fields.size >= 2) { "login form not found" }
    fields[0].text = email
    fields[1].text = password
    clickVisible(signIn)

    // Branch → terminal ("any free terminal" is preselected) → pair.
    val branch = checkNotNull(device.wait(Until.findObject(By.res(TAG_BRANCH)), SCREEN_TIMEOUT_MS)) { "no branch offered for this account" }
    branch.click()
    clickVisible(label("Continue", "ดำเนินการต่อ"))
    clickVisible(label("Pair device", "ผูกเครื่อง"), timeoutMs = SCREEN_TIMEOUT_MS)
    check(awaitObject(attractScreen)) { "pairing did not reach the attract screen" }
}

/**
 * A typical customer: attract → skip member → browse the menu → open option sheets → cart → payment
 * method. Stops before placing an order, so it can run any number of times against a real backend.
 */
internal fun MacrobenchmarkScope.orderJourney() {
    clickVisible(attractScreen)
    clickVisible(label("Skip", "ข้าม"))
    browseMenu()

    // Open the first few products: an option sheet gets scrolled and dismissed, a plain product lands in the cart.
    for (index in 0 until 4) {
        if (!clickProduct(index)) break
        if (device.wait(Until.hasObject(By.res(TAG_OPTION_DIALOG)), 1_500)) scrollAndCloseOptionSheet()
    }

    // The bottom bar adds the total ("View cart · ฿120"); the wide-screen cart panel says just "View cart".
    val viewCart = device.findObject(By.pkg(TARGET_PACKAGE).text(Pattern.compile("(View cart|ดูตะกร้า).*")))
    if (viewCart != null && viewCart.isEnabled) {
        viewCart.click()
        clickVisible(label("Go to payment", "ไปชำระเงิน"))
        device.wait(Until.hasObject(label("Choose payment method", "เลือกวิธีชำระเงิน")), SCREEN_TIMEOUT_MS)
        device.pressBack()
    }
}

/** Waits for the menu grid and scrolls it down and back up, like a customer browsing. */
internal fun MacrobenchmarkScope.browseMenu() {
    val grid = checkNotNull(device.wait(Until.findObject(By.res(TAG_MENU_GRID)), SCREEN_TIMEOUT_MS)) { "menu grid not shown" }
    grid.flingBothWays()
    device.waitForIdle()
}

/** Attract → skip member → menu, for benchmarks that measure the menu itself. */
internal fun MacrobenchmarkScope.openMenu() {
    check(awaitObject(attractScreen)) { "attract screen not shown (is the kiosk paired?)" }
    clickVisible(attractScreen)
    clickVisible(label("Skip", "ข้าม"))
    device.wait(Until.hasObject(By.res(TAG_MENU_GRID)), SCREEN_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.waitForAttract() {
    awaitObject(attractScreen)
}

/**
 * The menu's heaviest first-time work: scroll the grid (a big menu scrolls; a short one fits the screen), then
 * open the first product that has an option sheet, scroll the sheet and close it.
 */
internal fun MacrobenchmarkScope.useMenu() {
    checkNotNull(device.findObject(By.res(TAG_MENU_GRID))) { "menu grid not shown" }.flingBothWays()
    device.waitForIdle()
    for (index in 0 until 4) {
        if (!clickProduct(index)) break
        if (device.wait(Until.hasObject(By.res(TAG_OPTION_DIALOG)), 1_500)) {
            scrollAndCloseOptionSheet()
            return
        }
    }
    error("no product with an option sheet in the first four")
}

private fun MacrobenchmarkScope.scrollAndCloseOptionSheet() {
    // The sheet shows a loader until the product detail arrives; its content is the scrollable part.
    device.wait(Until.hasObject(By.res(TAG_OPTION_DIALOG).hasDescendant(By.scrollable(true))), 5_000)
    device.findObject(By.res(TAG_OPTION_DIALOG))?.findObject(By.scrollable(true))?.fling(Direction.DOWN)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.gone(By.res(TAG_OPTION_DIALOG)), 3_000)
}

/**
 * Taps the [index]th visible product card. A long menu can still be settling after a fling, which recycles
 * the card under the finger (StaleObjectException): look it up again and retry.
 */
private fun MacrobenchmarkScope.clickProduct(index: Int): Boolean {
    repeat(3) {
        val card = device.findObjects(By.res(TAG_PRODUCT)).getOrNull(index) ?: return false
        try {
            card.click()
            return true
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    return false
}

private fun UiObject2.flingBothWays() {
    // Keep gestures away from the screen edges (system gesture areas).
    setGestureMargin(visibleBounds.width() / 5)
    fling(Direction.DOWN)
    fling(Direction.UP)
}

/**
 * Clicks the first match once it is actually on screen, scrolling the page up to a few times: setup and
 * cart screens scroll, and a node below the fold still exists in the tree but can't be tapped.
 */
private fun MacrobenchmarkScope.clickVisible(selector: BySelector, timeoutMs: Long = 10_000) {
    check(awaitObject(selector, timeoutMs)) { "not found: $selector" }
    repeat(5) {
        val target = device.findObject(selector)
        val bounds = target?.visibleBounds
        if (target != null && bounds != null && !bounds.isEmpty && bounds.bottom <= device.displayHeight) {
            target.click()
            device.waitForIdle()
            return
        }
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
        device.waitForIdle()
    }
    error("never became visible: $selector")
}
