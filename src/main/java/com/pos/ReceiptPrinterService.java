package com.pos;

public interface ReceiptPrinterService {
    void print(Sale sale, String paid, String change) throws Exception;
}
