package com.pos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AccountingJournalEntryRequest {
    private LocalDate entryDate;
    private String description;
    private String referenceType;
    private Long referenceId;
    private Long branchId;
    private List<AccountingJournalLineRequest> lines = new ArrayList<>();

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public List<AccountingJournalLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<AccountingJournalLineRequest> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
