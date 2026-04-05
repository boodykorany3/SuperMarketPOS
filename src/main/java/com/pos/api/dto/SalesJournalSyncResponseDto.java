package com.pos.api.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesJournalSyncResponseDto {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private int processedSales;
    private int createdEntries;
    private int existingEntries;
    private int skippedSales;
    private int failedEntries;
    private List<String> failedInvoices = new ArrayList<>();

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public int getProcessedSales() {
        return processedSales;
    }

    public void setProcessedSales(int processedSales) {
        this.processedSales = processedSales;
    }

    public int getCreatedEntries() {
        return createdEntries;
    }

    public void setCreatedEntries(int createdEntries) {
        this.createdEntries = createdEntries;
    }

    public int getExistingEntries() {
        return existingEntries;
    }

    public void setExistingEntries(int existingEntries) {
        this.existingEntries = existingEntries;
    }

    public int getSkippedSales() {
        return skippedSales;
    }

    public void setSkippedSales(int skippedSales) {
        this.skippedSales = skippedSales;
    }

    public int getFailedEntries() {
        return failedEntries;
    }

    public void setFailedEntries(int failedEntries) {
        this.failedEntries = failedEntries;
    }

    public List<String> getFailedInvoices() {
        return failedInvoices;
    }

    public void setFailedInvoices(List<String> failedInvoices) {
        this.failedInvoices = failedInvoices;
    }
}
