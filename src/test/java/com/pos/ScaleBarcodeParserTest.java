package com.pos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScaleBarcodeParserTest {

    @Test
    void parseValidWeightedBarcode() {
        String barcode = buildScaleBarcode("21", "12345", 750);

        ScaleBarcodeData data = ScaleBarcodeParser.parse(barcode);

        assertNotNull(data);
        assertEquals("21", data.prefix());
        assertEquals("12345", data.productCode());
        assertEquals(750, data.grams());
        assertEquals(0.750, data.weightKg(), 0.00001);
    }

    @Test
    void rejectBarcodeWithInvalidCheckDigit() {
        String valid = buildScaleBarcode("21", "54321", 250);
        char wrongCheckDigit = valid.charAt(12) == '9' ? '8' : '9';
        String invalid = valid.substring(0, 12) + wrongCheckDigit;

        ScaleBarcodeData data = ScaleBarcodeParser.parse(invalid);

        assertNull(data);
    }

    @Test
    void rejectBarcodeWithUnsupportedPrefix() {
        String barcode = buildScaleBarcode("40", "12345", 500);

        ScaleBarcodeData data = ScaleBarcodeParser.parse(barcode);

        assertNull(data);
    }

    private String buildScaleBarcode(String prefix, String productCode, int grams) {
        String body = prefix + productCode + String.format("%05d", grams);
        int check = computeEan13CheckDigit(body);
        return body + check;
    }

    private int computeEan13CheckDigit(String firstTwelveDigits) {
        int sum = 0;
        for (int i = 0; i < firstTwelveDigits.length(); i++) {
            int digit = firstTwelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
