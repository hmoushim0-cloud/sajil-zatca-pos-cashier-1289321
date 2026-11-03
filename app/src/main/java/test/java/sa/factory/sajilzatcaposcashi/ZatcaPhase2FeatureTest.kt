package sa.factory.sajilzatcaposcashi

import sa.factory.sajilzatcaposcashi.hardware.EscPosReceiptCompiler
import sa.factory.sajilzatcaposcashi.model.SajilZatcaItem
import sa.factory.sajilzatcaposcashi.model.SajilZatcaOrder
import sa.factory.sajilzatcaposcashi.zatca.ZatcaInvoiceData
import sa.factory.sajilzatcaposcashi.zatca.ZatcaPhase2QrEncoder
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.Date

/**
 * Regression and Feature Test Suite for Sidekick Autonomous Enhancement.
 * Tests ZATCA Phase 2 TLV QR encoder and ESC/POS thermal receipt byte compiler.
 * Zero-Emoji Invariant verified.
 */
class ZatcaPhase2FeatureTest {

    @Test
    fun testSaudiVatNumberValidation() {
        // Valid 15-digit Saudi VAT starting and ending with 3
        val validVat = "310123456700003"
        assertTrue(ZatcaPhase2QrEncoder.isValidSaudiVat(validVat))

        // Invalid: does not start with 3
        val invalidStart = "110123456700003"
        assertFalse(ZatcaPhase2QrEncoder.isValidSaudiVat(invalidStart))

        // Invalid: length != 15
        val invalidLength = "3101234567"
        assertFalse(ZatcaPhase2QrEncoder.isValidSaudiVat(invalidLength))
    }

    @Test
    fun testZatcaTlvQrEncodingStructure() {
        val testDate = Date(1762164000000L) // 2025-11-03 UTC
        val data = ZatcaInvoiceData(
            sellerName = "مؤسسة سجل لحلول نقاط البيع",
            vatRegistrationNumber = "310123456700003",
            invoiceTimestamp = testDate,
            invoiceTotalSar = 345.00,
            vatAmountSar = 45.00
        )

        val base64Tlv = ZatcaPhase2QrEncoder.encodeToTlvBase64(data)
        assertNotNull(base64Tlv)
        assertTrue("Base64 string must not be empty", base64Tlv.length > 20)

        // Decode back and check Tag 1 (Seller Name) and Tag 2 (VAT)
        val decodedBytes = Base64.getDecoder().decode(base64Tlv)
        assertEquals("First byte must be Tag 1", 1.toByte(), decodedBytes[0])
        val tag1Length = decodedBytes[1].toInt()
        assertTrue("Tag 1 length must be greater than 0", tag1Length > 0)

        val sellerNameExtracted = String(decodedBytes, 2, tag1Length, Charsets.UTF_8)
        assertEquals("مؤسسة سجل لحلول نقاط البيع", sellerNameExtracted)

        // Tag 2 must follow Tag 1
        val tag2Offset = 2 + tag1Length
        assertEquals("Byte after Tag 1 must be Tag 2", 2.toByte(), decodedBytes[tag2Offset])
    }

    @Test
    fun testEscPosReceiptCompilerOutput() {
        val compiler = EscPosReceiptCompiler()
        val items = listOf(
            SajilZatcaItem("i1", "طابعة إيصالات حرارية 80 مم", "ESC/POS Thermal Printer", 800.0, "معتمد", "أجهزة"),
            SajilZatcaItem("i2", "قارئ باركود لاسلكي 2D", "2D Wireless Barcode Scanner", 400.0, "معتمد", "أجهزة")
        )
        val order = SajilZatcaOrder(
            orderId = "ZATCA-98210",
            items = items,
            subtotalSar = 1200.0,
            vatSar = 180.0,
            totalSar = 1380.0,
            paymentMethod = "Mada Contactless EMV",
            timestamp = System.currentTimeMillis()
        )

        val byteStream = compiler.compileReceipt(
            storeName = "مؤسسة سجل لحلول نقاط البيع",
            vatNumber = "310123456700003",
            order = order
        )

        assertNotNull(byteStream)
        assertTrue("Bytecode must contain substantive ESC/POS commands", byteStream.size > 150)

        // Verify ESC @ (Printer Initialization 0x1B, 0x40)
        assertEquals(0x1B.toByte(), byteStream[0])
        assertEquals(0x40.toByte(), byteStream[1])

        // Verify GS V (Paper Cut 0x1D, 0x56) at the end of stream
        val lastIdx = byteStream.size - 1
        assertEquals(0x00.toByte(), byteStream[lastIdx])
        assertEquals(0x42.toByte(), byteStream[lastIdx - 1])
        assertEquals(0x56.toByte(), byteStream[lastIdx - 2])
        assertEquals(0x1D.toByte(), byteStream[lastIdx - 3])
    }
}
