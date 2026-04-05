package com.pos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PosPaymentServiceTest {

    @Test
    void parsePaidAmountReturnsNullForInvalidValue() {
        assertNull(PosPaymentService.parsePaidAmount("x1"));
    }

    @Test
    void isEnoughPaidWorksForBoundary() {
        assertTrue(PosPaymentService.isEnoughPaid(100.0, 100.0));
        assertFalse(PosPaymentService.isEnoughPaid(99.99, 100.0));
    }
}
