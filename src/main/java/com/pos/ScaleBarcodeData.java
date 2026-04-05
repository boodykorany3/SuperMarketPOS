package com.pos;

public record ScaleBarcodeData(
        String scannedBarcode,
        String prefix,
        String productCode,
        int grams,
        double weightKg
) {
}
