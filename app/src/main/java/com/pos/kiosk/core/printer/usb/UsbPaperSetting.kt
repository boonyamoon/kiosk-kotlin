package com.pos.kiosk.core.printer.usb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pos.kiosk.core.printer.builtin.PAPER_58MM_DOTS
import com.pos.kiosk.core.printer.builtin.PAPER_80MM_DOTS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printerDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_printer")

/** The paper in a USB printer. USB has no way of reporting it, so staff choose it in the admin menu. */
interface UsbPaperSetting {
    suspend fun widthDots(): Int

    suspend fun setWidthDots(dots: Int)
}

/** Belongs to the device rather than the pairing, so it stays through an unpair. Most kiosks print on 80 mm. */
@Singleton
class DataStoreUsbPaperSetting @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UsbPaperSetting {

    override suspend fun widthDots(): Int {
        val saved = try {
            context.printerDataStore.data.first()[WIDTH_DOTS]
        } catch (_: IOException) {
            null
        }
        return if (saved == PAPER_58MM_DOTS) PAPER_58MM_DOTS else PAPER_80MM_DOTS
    }

    override suspend fun setWidthDots(dots: Int) {
        try {
            context.printerDataStore.edit { it[WIDTH_DOTS] = dots }
        } catch (_: IOException) {
            // The menu reads the width back, so staff see the choice didn't stick.
        }
    }

    private companion object {
        val WIDTH_DOTS = intPreferencesKey("usb_paper_width_dots")
    }
}
