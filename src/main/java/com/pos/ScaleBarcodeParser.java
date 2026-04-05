package com.pos;

import java.util.Set;

public final class ScaleBarcodeParser {

    private static final Set<String> WEIGHT_PREFIXES = Set.of(
            "20", "21", "22", "23", "24", "25", "26", "27", "28", "29"
    );

    private ScaleBarcodeParser() {}

    public static ScaleBarcodeData parse(String barcode) {
        if (barcode == null || barcode.length() != 13) {
            return null;
        }
        if (!isDigitsOnly(barcode)) {
            return null;
        }

        String prefix = barcode.substring(0, 2);
        if (!WEIGHT_PREFIXES.contains(prefix)) {
            return null;
        }
        if (!hasValidEan13CheckDigit(barcode)) {
            return null;
        }

        String productCode = barcode.substring(2, 7);
        int grams = Integer.parseInt(barcode.substring(7, 12));
        if (grams <= 0) {
            return null;
        }

        return new ScaleBarcodeData(
                barcode,
                prefix,
                productCode,
                grams,
                grams / 1000.0
        );
    }

    static boolean hasValidEan13CheckDigit(String code) {
        if (code == null || code.length() != 13 || !isDigitsOnly(code)) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = code.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        int expected = (10 - (sum % 10)) % 10;
        int actual = code.charAt(12) - '0';
        return expected == actual;
    }

    private static boolean isDigitsOnly(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
