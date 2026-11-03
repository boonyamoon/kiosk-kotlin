package com.pos.kiosk

import android.app.admin.DeviceAdminReceiver

/** Device-owner hook so MainActivity can enter Lock Task Mode without the screen-pinning prompt. */
class KioskDeviceAdminReceiver : DeviceAdminReceiver()
