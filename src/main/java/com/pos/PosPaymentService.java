package com.pos;

public final class PosPaymentService {

    private PosPaymentService() {}

    public static Double parsePaidAmount(String paidText) {
        try {
            return Double.parseDouble(paidText);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isEnoughPaid(double paid, double total) {
        return paid >= total;
    }
}
