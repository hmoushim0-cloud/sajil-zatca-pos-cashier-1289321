package sa.factory.sajilzatcaposcashi.zatca

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ZATCA (FATOORA) Phase 2 Cryptographic TLV QR-Code Encoder.
 * Strictly compliant with ZATCA E-Invoicing requirements for the Kingdom of Saudi Arabia.
 * Zero-Emoji Invariant strictly maintained.
 */
data class ZatcaInvoiceData(
    val sellerName: String,
    val vatRegistrationNumber: String,
    val invoiceTimestamp: Date,
    val invoiceTotalSar: Double,
    val vatAmountSar: Double,
    val invoiceHashSha256: String = "",
    val ecdsaSignature: ByteArray = ByteArray(0)
)

object ZatcaPhase2QrEncoder {

    private const val TAG_SELLER_NAME: Byte = 1
    private const val TAG_VAT_NUMBER: Byte = 2
    private const val TAG_TIMESTAMP: Byte = 3
    private const val TAG_INVOICE_TOTAL: Byte = 4
    private const val TAG_VAT_AMOUNT: Byte = 5
    private const val TAG_INVOICE_HASH: Byte = 6
    private const val TAG_DIGITAL_SIGNATURE: Byte = 7

    /**
     * Formats date into ZATCA compliant ISO 8601 UTC format (YYYY-MM-DD'T'HH:mm:ss'Z').
     */
    fun formatZatcaDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    /**
     * Serializes invoice attributes into binary Tag-Length-Value (TLV) structure
     * and encodes to Base64 representation for 2D barcode generation.
     */
    fun encodeToTlvBase64(data: ZatcaInvoiceData): String {
        val stream = ByteArrayOutputStream()

        val formattedDate = formatZatcaDate(data.invoiceTimestamp)
        val formattedTotal = String.format(Locale.US, "%.2f", data.invoiceTotalSar)
        val formattedVat = String.format(Locale.US, "%.2f", data.vatAmountSar)

        // Tag 1: Seller Name
        writeTlvTag(stream, TAG_SELLER_NAME, data.sellerName.toByteArray(StandardCharsets.UTF_8))

        // Tag 2: VAT Registration Number (15 digits)
        writeTlvTag(stream, TAG_VAT_NUMBER, data.vatRegistrationNumber.toByteArray(StandardCharsets.UTF_8))

        // Tag 3: Invoice Timestamp
        writeTlvTag(stream, TAG_TIMESTAMP, formattedDate.toByteArray(StandardCharsets.UTF_8))

        // Tag 4: Invoice Total (with VAT)
        writeTlvTag(stream, TAG_INVOICE_TOTAL, formattedTotal.toByteArray(StandardCharsets.UTF_8))

        // Tag 5: VAT Amount
        writeTlvTag(stream, TAG_VAT_AMOUNT, formattedVat.toByteArray(StandardCharsets.UTF_8))

        // Tag 6: SHA-256 Invoice Cryptographic Hash (Optional for simplified, required for standard phase 2)
        if (data.invoiceHashSha256.isNotBlank()) {
            val hashBytes = data.invoiceHashSha256.toByteArray(StandardCharsets.UTF_8)
            writeTlvTag(stream, TAG_INVOICE_HASH, hashBytes)
        }

        // Tag 7: ECDSA Digital Signature (Phase 2 compliance)
        if (data.ecdsaSignature.isNotEmpty()) {
            writeTlvTag(stream, TAG_DIGITAL_SIGNATURE, data.ecdsaSignature)
        }

        val rawTlvBytes = stream.toByteArray()
        return Base64.getEncoder().encodeToString(rawTlvBytes)
    }

    /**
     * Generates a deterministic SHA-256 invoice payload hash.
     */
    fun calculateInvoiceHash(rawPayload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawPayload.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates Saudi VAT registration number structure (15 digits, starting and ending with 3).
     */
    fun isValidSaudiVat(vatNumber: String): Boolean {
        return vatNumber.length == 15 &&
               vatNumber.startsWith("3") &&
               vatNumber.endsWith("3") &&
               vatNumber.all { it.isDigit() }
    }

    private fun writeTlvTag(stream: ByteArrayOutputStream, tag: Byte, value: ByteArray) {
        stream.write(tag.toInt())
        stream.write(value.size)
        stream.write(value)
    }
}
