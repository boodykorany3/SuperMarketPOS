package com.pos;

public final class PosInvoiceService {

    private PosInvoiceService() {}

    public static double calculateRowTotal(int quantity, double unitPrice) {
        return quantity * unitPrice;
    }

    public static double calculateChange(double total, String paidText) {
        try {
            double paid = Double.parseDouble(paidText);
            return paid - total;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
