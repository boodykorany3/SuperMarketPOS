package com.pos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PosInvoiceServiceTest {

    @Test
    void calculateRowTotal() {
        assertEquals(37.5, PosInvoiceService.calculateRowTotal(3, 12.5), 0.00001);
    }

    @Test
    void calculateChangeWithInvalidPaidTextReturnsZero() {
        assertEquals(0.0, PosInvoiceService.calculateChange(100.0, "abc"), 0.00001);
    }
}
