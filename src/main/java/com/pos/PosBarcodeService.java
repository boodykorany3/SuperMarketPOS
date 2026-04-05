package com.pos;

public final class PosBarcodeService {

    private PosBarcodeService() {}

    public enum FailureReason {
        EMPTY,
        NOT_FOUND,
        WEIGHTED_PRODUCT_CODE_NOT_FOUND
    }

    public record ScanResult(
            Product product,
            String displayName,
            String scannedBarcode,
            String stockBarcode,
            double unitPrice,
            boolean weighted
    ) {}

    public record LookupResult(
            ScanResult scan,
            FailureReason failureReason,
            String details
    ) {
        public static LookupResult success(ScanResult scan) {
            return new LookupResult(scan, null, "");
        }

        public static LookupResult failure(FailureReason reason, String details) {
            return new LookupResult(null, reason, details == null ? "" : details);
        }

        public boolean isSuccess() {
            return scan != null;
        }
    }

    public static LookupResult resolve(String rawLookup) {
        String lookup = InventoryStore.sanitizeBarcode(rawLookup);
        if (lookup.isEmpty() && rawLookup != null) {
            lookup = rawLookup.trim();
        }
        if (lookup.isEmpty()) {
            return LookupResult.failure(FailureReason.EMPTY, "");
        }

        Product direct = InventoryStore.findByBarcodeFlexible(lookup);
        if (direct == null) {
            direct = InventoryStore.findByName(lookup);
        }
        if (direct != null) {
            return LookupResult.success(new ScanResult(
                    direct,
                    direct.getName(),
                    direct.getBarcode(),
                    direct.getBarcode(),
                    direct.getPrice(),
                    false
            ));
        }

        ScaleBarcodeData scaleData = ScaleBarcodeParser.parse(lookup);
        if (scaleData != null) {
            Product weightedProduct = InventoryStore.findByBarcodeFlexible(scaleData.productCode());
            if (weightedProduct == null) {
                return LookupResult.failure(FailureReason.WEIGHTED_PRODUCT_CODE_NOT_FOUND, scaleData.productCode());
            }

            return LookupResult.success(new ScanResult(
                    weightedProduct,
                    String.format("%s (%.3f kg)", weightedProduct.getName(), scaleData.weightKg()),
                    scaleData.scannedBarcode(),
                    weightedProduct.getBarcode(),
                    weightedProduct.getPrice() * scaleData.weightKg(),
                    true
            ));
        }

        return LookupResult.failure(FailureReason.NOT_FOUND, "");
    }
}
