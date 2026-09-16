package com.example

import com.example.ui.components.SmartImeiExtractor
import org.junit.Assert.*
import org.junit.Test

class SmartImeiExtractorTest {

    @Test
    fun testExtractImeiFromStandardBarcode() {
        val imei = "358912345678901"
        val extracted = SmartImeiExtractor.extractImeiFromBarcode(imei)
        assertEquals("358912345678901", extracted)
    }

    @Test
    fun testExtractImeiFrom16DigitImeisvBarcode() {
        val imeisv = "3589123456789012"
        val extracted = SmartImeiExtractor.extractImeiFromBarcode(imeisv)
        assertEquals("358912345678901", extracted)
    }

    @Test
    fun testExtractImeiFromBoxLabelText() {
        val boxText = """
            SAMSUNG Galaxy S24 Ultra 512GB Titanium Black
            Model: SM-S928B/DS
            IMEI 1: 359123456789012
            IMEI 2: 359123456789013
            S/N: R5CX1092ABC
            Made in Vietnam
        """.trimIndent()

        val imeis = SmartImeiExtractor.extractImeisFromText(boxText)
        assertTrue(imeis.contains("359123456789012"))
        assertTrue(imeis.contains("359123456789013"))
        assertEquals(2, imeis.size)
    }

    @Test
    fun testExtractFormattedImeiWithSpacesAndHyphens() {
        val textOnSticker = "IMEI: 86 9403 92 817263 5"
        val imeis = SmartImeiExtractor.extractImeisFromText(textOnSticker)
        assertEquals(1, imeis.size)
        assertEquals("869403928172635", imeis.first())
    }

    @Test
    fun testExtractFromPhoneScreenText() {
        val screenText = """
            Device Information
            Status
            IMEI (slot 1)
            354892091234567
            IMEI SV
            01
        """.trimIndent()

        val imeis = SmartImeiExtractor.extractImeisFromText(screenText)
        assertTrue(imeis.contains("354892091234567"))
    }

    @Test
    fun testRejectInvalidCandidates() {
        assertFalse(SmartImeiExtractor.isValidImeiCandidate("000000000000000"))
        assertFalse(SmartImeiExtractor.isValidImeiCandidate("12345"))
        assertFalse(SmartImeiExtractor.isValidImeiCandidate("35891234567890A"))
        assertTrue(SmartImeiExtractor.isValidImeiCandidate("358912345678901"))
    }
}
