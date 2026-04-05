package com.pos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class JournalLineRequestDto {

    @NotNull(message = "accountId is required")
    private Long accountId;

    @NotNull(message = "debit is required")
    @DecimalMin(value = "0.00", message = "debit must be >= 0")
    private BigDecimal debit;

    @NotNull(message = "credit is required")
    @DecimalMin(value = "0.00", message = "credit must be >= 0")
    private BigDecimal credit;

    @Size(max = 240, message = "note must be at most 240 characters")
    private String note;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
