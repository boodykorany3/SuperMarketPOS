package com.pos;

public class PrinterServiceFactory {

    public static ReceiptPrinterService create(PrinterSettings settings) {
        if (settings == null || settings.getMode() == PrinterSettings.Mode.NONE) {
            return new NoOpPrinterService();
        }
        return new EscPosPrinterService(settings);
    }
}
