package com.pos.kiosk.core.printer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.pos.kiosk.R
import com.pos.kiosk.core.common.formatAmount
import com.pos.kiosk.core.common.formatRate
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.OrderType
import com.pos.kiosk.domain.model.PaymentMethod
import com.pos.kiosk.domain.model.Receipt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws receipts with Android's text stack (correct Thai shaping) and converts them to a 1-bit
 * image for [EscPosRaster]. Receipts are always printed in Thai, whatever the screen language.
 */
@Singleton
class ReceiptRenderer @Inject constructor(@ApplicationContext context: Context) {

    private companion object {
        // Type sizes in dots on 80 mm (576-dot) paper, scaled down for 58 mm — matched to the iPad POS receipt.
        const val HEADING = 26f
        const val BODY = 24f
        const val SMALL = 22f
        const val TOTAL = 32f
        const val BUDDHIST_YEAR_OFFSET = 543
        const val POWERED_BY = "Powered by MeeD POS"
    }

    private val thai: Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("th-TH")) },
    )

    /**
     * Same layout and type sizes as the iPad POS receipt: centred shop header and document title, an info block,
     * one row per item (quantity · name · amount) with its options under it, the item count and totals, the
     * VAT block (net before VAT, VAT, satang rounding, grand total), the payment and "Powered by MeeD POS".
     * Kiosk additions: the queue number and order type, and an "unpaid" banner on a pay-at-counter slip.
     */
    fun render(receipt: Receipt, config: KioskConfig, widthDots: Int): MonochromeImage {
        val settings = config.receiptSettings
        val branch = config.branch
        val doc = Doc(widthDots)
        val s = doc.scale
        val center = Layout.Alignment.ALIGN_CENTER

        // Shop header.
        doc.space(12 * s)
        doc.text(branch.businessName, HEADING * s, bold = true, align = center)
        if (branch.branchName.isNotBlank()) doc.text(branch.branchName, BODY * s, align = center)
        branch.branchCode?.let { doc.text(thai.getString(R.string.receipt_branch_no, it), SMALL * s, align = center) }
        listOfNotNull(branch.addressLine1, branch.addressLine2).filter { it.isNotBlank() }.forEach { doc.text(it, SMALL * s, align = center) }
        branch.phone?.let { doc.text(thai.getString(R.string.receipt_phone, it), SMALL * s, align = center) }
        branch.taxId?.takeIf { it.isNotBlank() }?.let { doc.text(thai.getString(R.string.receipt_tax_id, it), SMALL * s, align = center) }

        // Document title: the shop's own receipt heading once paid; a pay-at-counter slip is only an order slip.
        doc.space(6 * s)
        val title = if (receipt.isPaid) settings?.header?.takeIf { it.isNotBlank() } ?: thai.getString(R.string.receipt_title_paid) else thai.getString(R.string.receipt_title_unpaid)
        doc.text(title, HEADING * s, bold = true, align = center)
        val showTax = settings?.showTax != false && receipt.taxRate > 0
        if (showTax) {
            doc.text(thai.getString(if (receipt.taxIncluded) R.string.receipt_prices_include_vat else R.string.receipt_prices_exclude_vat), BODY * s, align = center)
        }
        if (!receipt.isPaid) {
            doc.space(6 * s)
            doc.banner(thai.getString(R.string.receipt_unpaid_banner), 34 * s)
        }
        receipt.queueNumber?.let {
            doc.space(6 * s)
            doc.text(thai.getString(R.string.receipt_queue), BODY * s, align = center)
            doc.text(it, 96 * s, bold = true, align = center)
        }
        val orderType = if (receipt.orderType == OrderType.TAKE_AWAY) R.string.attract_take_away else R.string.attract_dine_in
        doc.text(thai.getString(orderType), BODY * s, bold = true, align = center)
        doc.divider()

        // Info block.
        // The receipt's running number once the backend issued one; until then (e.g. a pay-at-counter slip) the order number.
        receipt.receiptNumber?.let { doc.text(thai.getString(R.string.receipt_number, it), BODY * s) }
        if (receipt.receiptNumber != receipt.orderNumber) doc.text(thai.getString(R.string.receipt_order_no, receipt.orderNumber), BODY * s)
        doc.text(thai.getString(R.string.receipt_terminal, config.terminalName), BODY * s)
        receipt.memberName?.let { doc.text(thai.getString(R.string.receipt_member, it), BODY * s) }
        val millis = if (receipt.createdAtMillis > 0) receipt.createdAtMillis else System.currentTimeMillis()
        doc.row(thai.getString(R.string.receipt_date, buddhistDate(millis)), thai.getString(R.string.receipt_time, time(millis)), BODY * s)
        doc.divider()

        // Items.
        val showOptions = settings?.showMenuOptions ?: true
        val items = receipt.items.filter { !(settings?.hideZeroPriceItems == true && it.lineTotal == 0.0) }
        items.forEach { item ->
            val name = if (item.skuName.isNullOrBlank() || item.skuName == item.name) item.name else "${item.name} (${item.skuName})"
            val details = (if (showOptions) item.options else emptyList()) +
                listOfNotNull(item.note?.takeIf { it.isNotBlank() }?.let { thai.getString(R.string.receipt_item_note, it) })
            doc.itemRow(quantityText(item.quantity), name, formatAmount(item.lineTotal), details, BODY * s, SMALL * s)
        }
        doc.divider()

        // Count and subtotal.
        val count = items.sumOf { it.quantity }
        doc.row(thai.getString(R.string.receipt_item_count, quantityText(count)), "", BODY * s, leftBold = true)
        doc.row(thai.getString(R.string.receipt_subtotal), formatAmount(receipt.subtotal), BODY * s, leftBold = true)
        if (receipt.discount > 0) doc.row(thai.getString(R.string.receipt_discount), "-" + formatAmount(receipt.discount), BODY * s)
        if (receipt.serviceCharge > 0) {
            doc.row(thai.getString(R.string.receipt_service_charge, formatRate(receipt.serviceChargeRate)), formatAmount(receipt.serviceCharge), BODY * s)
        }
        doc.doubleDivider()

        // VAT block and grand total.
        if (showTax) {
            doc.row(thai.getString(R.string.receipt_before_vat), formatAmount(receipt.beforeTax), BODY * s, leftBold = true)
            doc.row(thai.getString(R.string.receipt_vat_amount, formatRate(receipt.taxRate)), formatAmount(receipt.taxAmount), BODY * s, leftBold = true)
        }
        if (receipt.rounding != 0.0) {
            val sign = if (receipt.rounding > 0) "+" else "-"
            doc.row(thai.getString(R.string.receipt_rounding), sign + formatAmount(kotlin.math.abs(receipt.rounding)), BODY * s, leftBold = true)
        }
        doc.row(thai.getString(R.string.receipt_total), formatAmount(receipt.total), BODY * s, leftBold = true, rightBold = true, rightSizePx = TOTAL * s)
        doc.doubleDivider()

        // Payment.
        when {
            !receipt.isPaid -> doc.text(thai.getString(R.string.receipt_pay_at_counter), BODY * s, bold = true, align = center)
            else -> {
                val method = if (receipt.paymentMethod == PaymentMethod.PROMPTPAY) R.string.receipt_paid_by_promptpay else R.string.receipt_paid_by_cash
                val paid = if (receipt.paidAmount > 0) receipt.paidAmount else receipt.total
                doc.row(thai.getString(method), formatAmount(paid), BODY * s, leftBold = true)
                if (receipt.paymentMethod != PaymentMethod.PROMPTPAY) {
                    doc.row(thai.getString(R.string.receipt_change), formatAmount((paid - receipt.total).coerceAtLeast(0.0)), BODY * s, leftBold = true)
                }
            }
        }
        doc.divider()

        listOfNotNull(settings?.footerLine1, settings?.footerLine2).filter { it.isNotBlank() }
            .forEach { doc.text(it, SMALL * s, align = center) }
        doc.space(40 * s)
        doc.text(POWERED_BY, SMALL * s, align = center)
        doc.space(12 * s)
        return doc.toMonochrome()
    }

    fun renderTestPage(config: KioskConfig, widthDots: Int): MonochromeImage {
        val doc = Doc(widthDots)
        val s = doc.scale
        doc.text(thai.getString(R.string.receipt_test_title), 30 * s, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        doc.divider()
        doc.row(config.terminalName, config.terminalCode, 22 * s)
        doc.text(config.branch.businessName + " · " + config.branch.branchName, 22 * s)
        config.receiptPrinter?.let { doc.text("${it.name} ${it.host}:${it.port} (${it.paperSize})", 20 * s) }
        doc.text(formatDate(System.currentTimeMillis()), 20 * s)
        doc.banner(thai.getString(R.string.receipt_unpaid_banner), 34 * s)
        doc.text("ภาษาไทย ก่ำ ปั้น ฟ้าผ่า ญี่ปุ่น ฎีกา ฤๅษี", 24 * s)
        return doc.toMonochrome()
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(if (millis > 0) millis else System.currentTimeMillis()))

    /** 18/02/2569 — Thai receipts use the Buddhist year, as the iPad does. */
    private fun buddhistDate(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return SimpleDateFormat("dd/MM/", Locale.US).format(Date(millis)) + (calendar.get(Calendar.YEAR) + BUDDHIST_YEAR_OFFSET)
    }

    private fun time(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    private fun quantityText(quantity: Double): String = if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

    /**
     * Collects blocks top-to-bottom, then paints them onto one bitmap of exactly the right height. Content sits
     * inside a small side margin, like the iPad receipt; [width] is the printable width inside it.
     */
    private class Doc(private val paperWidth: Int) {
        val scale: Float = paperWidth / 576f
        private val margin = (16 * scale).toInt()
        private val width = paperWidth - margin * 2
        private val blocks = mutableListOf<Pair<Int, (Canvas, Float) -> Unit>>()

        private fun paint(sizePx: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = sizePx
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun layout(text: String, paint: TextPaint, layoutWidth: Int, align: Layout.Alignment) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth.coerceAtLeast(1))
                .setAlignment(align)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.1f)
                .build()

        fun text(text: String, sizePx: Float, bold: Boolean = false, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val layout = layout(text, paint(sizePx, bold), width, align)
            blocks += (layout.height + lineGap() / 2) to { canvas, top ->
                canvas.save()
                canvas.translate(0f, top)
                layout.draw(canvas)
                canvas.restore()
            }
        }

        /**
         * Label left, amount right, on a shared baseline (the amount may be bigger, like the grand total).
         * A long label wraps inside the space the amount leaves.
         */
        fun row(
            left: String,
            right: String,
            sizePx: Float,
            leftBold: Boolean = false,
            rightBold: Boolean = false,
            rightSizePx: Float = sizePx,
        ) {
            val leftPaint = paint(sizePx, leftBold)
            val rightPaint = paint(rightSizePx, rightBold)
            val rightWidth = rightPaint.measureText(right)
            val gap = 12 * scale
            val leftLayout = layout(left, leftPaint, (width - rightWidth - gap).toInt(), Layout.Alignment.ALIGN_NORMAL)
            val leftBaseline = leftLayout.getLineBaseline(0).toFloat()
            val rightBaseline = -rightPaint.ascent()
            val baseline = maxOf(leftBaseline, rightBaseline)
            val height = maxOf(leftLayout.height + (baseline - leftBaseline), baseline + rightPaint.descent()).toInt() + lineGap()
            blocks += height to { canvas, top ->
                canvas.save()
                canvas.translate(0f, top + baseline - leftBaseline)
                leftLayout.draw(canvas)
                canvas.restore()
                canvas.drawText(right, width - rightWidth, top + baseline, rightPaint)
            }
        }

        /** "1  Beef Rendang      234.16": quantity column, wrapping name, amount right; [details] as "• …" under the name. */
        fun itemRow(quantity: String, name: String, amount: String, details: List<String>, sizePx: Float, detailSizePx: Float) {
            val paint = paint(sizePx, bold = false)
            val qtyWidth = maxOf(paint.measureText(quantity), paint.measureText("00")) + 16 * scale
            val amountWidth = paint.measureText(amount)
            val gap = 12 * scale
            val nameLayout = layout(name, paint, (width - qtyWidth - amountWidth - gap).toInt(), Layout.Alignment.ALIGN_NORMAL)
            val detailPaint = paint(detailSizePx, bold = false)
            val detailLayouts = details.map { layout("\u2022 $it", detailPaint, (width - qtyWidth - 8 * scale).toInt(), Layout.Alignment.ALIGN_NORMAL) }
            val baseline = nameLayout.getLineBaseline(0).toFloat()
            val height = nameLayout.height + detailLayouts.sumOf { it.height } + lineGap() * 2
            blocks += height to { canvas, top ->
                canvas.drawText(quantity, 0f, top + baseline, paint)
                canvas.drawText(amount, width - amountWidth, top + baseline, paint)
                canvas.save()
                canvas.translate(qtyWidth, top)
                nameLayout.draw(canvas)
                var y = nameLayout.height.toFloat()
                detailLayouts.forEach { detail ->
                    canvas.save()
                    canvas.translate(8 * scale, y)
                    detail.draw(canvas)
                    canvas.restore()
                    y += detail.height
                }
                canvas.restore()
            }
        }

        /** A little air between rows, scaled with the paper. */
        private fun lineGap(): Int = (6 * scale).toInt()

        fun banner(text: String, sizePx: Float) {
            val paint = paint(sizePx, bold = true).apply { color = Color.WHITE }
            val padding = (10 * scale).toInt()
            val layout = layout(text, paint, width, Layout.Alignment.ALIGN_CENTER)
            val height = layout.height + padding * 2
            blocks += height to { canvas, top ->
                canvas.drawRect(0f, top, width.toFloat(), top + height, Paint().apply { color = Color.BLACK })
                canvas.save()
                canvas.translate(0f, top + padding)
                layout.draw(canvas)
                canvas.restore()
            }
        }

        /** "– – – –" across the paper, as on the iPad receipt. */
        fun divider() = dashedLines(count = 1)

        /** "= = = =" — two dashed lines, around the totals. */
        fun doubleDivider() = dashedLines(count = 2)

        private fun dashedLines(count: Int) {
            val height = (28 * scale).toInt()
            val spacing = 5 * scale
            blocks += height to { canvas, top ->
                val paint = Paint().apply {
                    color = Color.BLACK
                    strokeWidth = maxOf(1f, 2 * scale)
                    pathEffect = DashPathEffect(floatArrayOf(14 * scale, 6 * scale), 0f)
                }
                val first = top + height / 2f - spacing * (count - 1) / 2f
                repeat(count) { i ->
                    val y = first + i * spacing
                    canvas.drawLine(0f, y, width.toFloat(), y, paint)
                }
            }
        }

        fun space(px: Float) {
            blocks += px.toInt() to { _, _ -> }
        }

        /**
         * Paints into an RGB_565 bitmap (2 bytes/dot instead of 4; black-on-white needs no alpha) and packs
         * it to 1 bit/dot a band of rows at a time, so no full-size int or boolean copy is ever allocated.
         * A long 576-dot receipt used to peak at ~8 bytes per dot; this peaks at ~2.
         */
        fun toMonochrome(): MonochromeImage {
            val height = blocks.sumOf { it.first }.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(paperWidth, height, Bitmap.Config.RGB_565)
            try {
                val canvas = Canvas(bitmap).apply {
                    drawColor(Color.WHITE)
                    translate(margin.toFloat(), 0f)
                }
                var top = 0f
                blocks.forEach { (blockHeight, draw) ->
                    draw(canvas, top)
                    top += blockHeight
                }
                val bytesPerRow = (paperWidth + 7) / 8
                val rows = ByteArray(bytesPerRow * height)
                val band = IntArray(paperWidth * PACK_BAND_ROWS)
                var y0 = 0
                while (y0 < height) {
                    val bandRows = minOf(PACK_BAND_ROWS, height - y0)
                    bitmap.getPixels(band, 0, paperWidth, 0, y0, paperWidth, bandRows)
                    for (dy in 0 until bandRows) {
                        val rowStart = (y0 + dy) * bytesPerRow
                        for (x in 0 until paperWidth) {
                            val c = band[dy * paperWidth + x]
                            val luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                            if (luminance < 128) {
                                val i = rowStart + x / 8
                                rows[i] = (rows[i].toInt() or (0x80 ushr (x % 8))).toByte()
                            }
                        }
                    }
                    y0 += bandRows
                }
                return MonochromeImage(paperWidth, height, rows)
            } finally {
                bitmap.recycle()
            }
        }

        private companion object {
            const val PACK_BAND_ROWS = 64
        }
    }
}
