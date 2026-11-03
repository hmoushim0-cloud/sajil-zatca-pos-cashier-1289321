package sa.factory.sajilzatcaposcashi.hardware

import sa.factory.sajilzatcaposcashi.model.SajilZatcaOrder
import sa.factory.sajilzatcaposcashi.zatca.ZatcaInvoiceData
import sa.factory.sajilzatcaposcashi.zatca.ZatcaPhase2QrEncoder
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale

/**
 * ESC/POS Thermal Receipt Byte Stream Compiler.
 * Compiles raw ESC/POS command sequences for 80mm and 58mm thermal printers.
 * Zero-Emoji Invariant strictly maintained.
 */
class EscPosReceiptCompiler {

    companion object {
        // Standard ESC/POS Control Sequences
        val ESC_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val ESC_EMPHASIZE_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val ESC_EMPHASIZE_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val ESC_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11
        val ESC_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
        val GS_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0 (feed and partial cut)
        val LINE_FEED = byteArrayOf(0x0A)
    }

    /**
     * Compiles an entire ZATCA-compliant thermal receipt into a byte array
     * ready for Bluetooth BLE or USB transmission to thermal printer hardware.
     */
    fun compileReceipt(
        storeName: String,
        vatNumber: String,
        order: SajilZatcaOrder
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Initialize Printer
        stream.write(ESC_INIT)

        // 2. Header (Centered, Double Size)
        stream.write(ESC_ALIGN_CENTER)
        stream.write(ESC_DOUBLE_SIZE)
        stream.write(storeName.toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)

        // Subheader (VAT Registration)
        stream.write(ESC_NORMAL_SIZE)
        stream.write("الرقم الضريبي: $vatNumber".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        stream.write("فاتورة ضريبية مبسطة (Simplified Tax Invoice)".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        stream.write("رقم الفاتورة: #${order.orderId}".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        stream.write("------------------------------------------------".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)

        // 3. Line Items (Left Aligned)
        stream.write(ESC_ALIGN_LEFT)
        for (item in order.items) {
            val itemLine = String.format(
                Locale.US,
                "%-28s %12.2f SAR",
                item.title,
                item.priceSar
            )
            stream.write(itemLine.toByteArray(StandardCharsets.UTF_8))
            stream.write(LINE_FEED)
        }

        stream.write("------------------------------------------------".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)

        // 4. Financial Totals (Right Aligned)
        stream.write(ESC_ALIGN_RIGHT)
        stream.write(String.format(Locale.US, "المجموع الفرعي (Subtotal): %10.2f SAR%n", order.subtotalSar).toByteArray(StandardCharsets.UTF_8))
        stream.write(String.format(Locale.US, "ضريبة القيمة المضافة 15%% (VAT): %10.2f SAR%n", order.vatSar).toByteArray(StandardCharsets.UTF_8))
        stream.write(ESC_EMPHASIZE_ON)
        stream.write(String.format(Locale.US, "المجموع الكلي (Total): %10.2f SAR%n", order.totalSar).toByteArray(StandardCharsets.UTF_8))
        stream.write(ESC_EMPHASIZE_OFF)
        stream.write(LINE_FEED)

        // 5. ZATCA Phase 2 TLV QR Code Generation
        val zatcaData = ZatcaInvoiceData(
            sellerName = storeName,
            vatRegistrationNumber = vatNumber,
            invoiceTimestamp = Date(order.timestamp),
            invoiceTotalSar = order.totalSar,
            vatAmountSar = order.vatSar
        )
        val qrPayload = ZatcaPhase2QrEncoder.encodeToTlvBase64(zatcaData)

        stream.write(ESC_ALIGN_CENTER)
        stream.write("[رمز الاستجابة السريع المعتمد زاتكا]".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        // Embed ESC/POS 2D Barcode (QR code model 2) command sequence
        appendEscPosQrCode(stream, qrPayload)

        stream.write(LINE_FEED)
        stream.write("طريقة الدفع: ${order.paymentMethod}".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        stream.write("شكرا لتعاملكم معنا".toByteArray(StandardCharsets.UTF_8))
        stream.write(LINE_FEED)
        stream.write(LINE_FEED)
        stream.write(LINE_FEED)

        // 6. Paper Cut
        stream.write(GS_PAPER_CUT)

        return stream.toByteArray()
    }

    /**
     * Appends ESC/POS GS ( k commands to encode and print a QR code.
     */
    private fun appendEscPosQrCode(stream: ByteArrayOutputStream, payload: String) {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val length = payloadBytes.size + 3
        val pL = (length and 0xFF).toByte()
        val pH = ((length shr 8) and 0xFF).toByte()

        // 1. Store QR symbol data in printer buffer
        // GS ( k pL pH cn fn m d1...dk (cn=49, fn=80, m=48)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 49, 80, 48))
        stream.write(payloadBytes)

        // 2. Set QR module size (CN=49, fn=67, n=6)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 49, 67, 0x06))

        // 3. Print the QR symbol (CN=49, fn=81, m=48)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 49, 81, 48))
    }
}
