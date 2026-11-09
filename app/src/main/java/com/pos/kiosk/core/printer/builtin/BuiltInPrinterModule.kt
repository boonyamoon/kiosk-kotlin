package com.pos.kiosk.core.printer.builtin

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import com.pos.kiosk.core.printer.usb.AndroidUsbPrinter
import com.pos.kiosk.core.printer.usb.NoUsbPrinter
import com.pos.kiosk.core.printer.usb.UsbPrinter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

enum class BuiltInPrinterKind { Sunmi, Imin, IminLegacy, None }

/** What identifies the hardware; a plain value so the choice below is unit-testable. */
data class DeviceProfile(
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val hasIminPrintService: Boolean,
)

private const val ANDROID_11 = 30
private const val IMIN_PRINT_SERVICE = "com.imin.printerservice"
private const val USB_ATTACH_ALIAS = "com.pos.kiosk.UsbPrinterAttached"
private const val USB_PERMISSION_ANSWER = "com.pos.kiosk.USB_PERMISSION_ANSWER"

/**
 * iMin splits its SDK by Android version: SDK 2.0 (a print service, Android 13 and up) and SDK 1.0 (direct USB,
 * Android 11 and below). "neostra" is iMin's former name, still used by some of its firmware.
 */
fun DeviceProfile.builtInPrinterKind(): BuiltInPrinterKind {
    val vendor = "$manufacturer $brand".lowercase()
    val isImin = "imin" in vendor || "neostra" in vendor || hasIminPrintService
    return when {
        "sunmi" in vendor -> BuiltInPrinterKind.Sunmi
        isImin && sdkInt <= ANDROID_11 -> BuiltInPrinterKind.IminLegacy
        hasIminPrintService -> BuiltInPrinterKind.Imin
        else -> BuiltInPrinterKind.None
    }
}

/** Only without a printer inside: the one in an old iMin is a USB printer too, and its SDK is the one driving it. */
val BuiltInPrinterKind.leavesRoomForUsbPrinter: Boolean get() = this == BuiltInPrinterKind.None

private fun Context.hasPackage(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun Context.deviceProfile() =
    DeviceProfile(Build.MANUFACTURER, Build.BRAND, Build.VERSION.SDK_INT, hasPackage(IMIN_PRINT_SERVICE))

/** The manifest keeps this off: see the comment there. */
private fun Context.enableUsbAttachAlias() {
    val alias = ComponentName(this, USB_ATTACH_ALIAS)
    val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    if (packageManager.getComponentEnabledSetting(alias) != enabled) {
        packageManager.setComponentEnabledSetting(alias, enabled, PackageManager.DONT_KILL_APP)
    }
}

/**
 * Android requires a PendingIntent, but nothing listens for it: the admin menu reads the permission when the kiosk
 * resumes, which also covers a prompt that never opened and so never answers.
 */
private fun Context.usbPermissionIntent(): PendingIntent = PendingIntent.getBroadcast(
    this,
    0,
    Intent(USB_PERMISSION_ANSWER).setPackage(packageName),
    PendingIntent.FLAG_IMMUTABLE,
)

@Module
@InstallIn(SingletonComponent::class)
object BuiltInPrinterModule {
    /** The vendor classes are only touched here, on their own hardware. */
    @Provides
    @Singleton
    fun builtInPrinter(@ApplicationContext context: Context): BuiltInPrinter =
        when (context.deviceProfile().builtInPrinterKind()) {
            BuiltInPrinterKind.Sunmi -> SunmiBuiltInPrinter(context)
            BuiltInPrinterKind.Imin -> IminBuiltInPrinter(context)
            BuiltInPrinterKind.IminLegacy -> IminLegacyBuiltInPrinter(context)
            BuiltInPrinterKind.None -> NoBuiltInPrinter
        }

    @Provides
    @Singleton
    fun usbPrinter(@ApplicationContext context: Context): UsbPrinter {
        if (!context.deviceProfile().builtInPrinterKind().leavesRoomForUsbPrinter) return NoUsbPrinter
        context.enableUsbAttachAlias()
        return AndroidUsbPrinter(context.getSystemService(UsbManager::class.java)) { context.usbPermissionIntent() }
    }
}
