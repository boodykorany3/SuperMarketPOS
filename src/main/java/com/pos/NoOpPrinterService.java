package com.pos;

public class NoOpPrinterService implements ReceiptPrinterService {
    @Override
    public void print(Sale sale, String paid, String change) {
        // Intentionally no-op when printer mode is disabled.
    }
}
