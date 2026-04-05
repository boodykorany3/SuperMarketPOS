package com.pos.api.dto;

import java.math.BigDecimal;

public class BranchOverviewDto {
    private Long branchId;
    private String branchCode;
    private String branchName;
    private BigDecimal todaySales = BigDecimal.ZERO;
    private Long todayInvoices = 0L;
    private BigDecimal currentMonthSales = BigDecimal.ZERO;
    private Long currentMonthInvoices = 0L;
    private BigDecimal previousMonthSales = BigDecimal.ZERO;
    private Long previousMonthInvoices = 0L;

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

    public BigDecimal getTodaySales() {
        return todaySales;
    }

    public void setTodaySales(BigDecimal todaySales) {
        this.todaySales = todaySales;
    }

    public Long getTodayInvoices() {
        return todayInvoices;
    }

    public void setTodayInvoices(Long todayInvoices) {
        this.todayInvoices = todayInvoices;
    }

    public BigDecimal getCurrentMonthSales() {
        return currentMonthSales;
    }

    public void setCurrentMonthSales(BigDecimal currentMonthSales) {
        this.currentMonthSales = currentMonthSales;
    }

    public Long getCurrentMonthInvoices() {
        return currentMonthInvoices;
    }

    public void setCurrentMonthInvoices(Long currentMonthInvoices) {
        this.currentMonthInvoices = currentMonthInvoices;
    }

    public BigDecimal getPreviousMonthSales() {
        return previousMonthSales;
    }

    public void setPreviousMonthSales(BigDecimal previousMonthSales) {
        this.previousMonthSales = previousMonthSales;
    }

    public Long getPreviousMonthInvoices() {
        return previousMonthInvoices;
    }

    public void setPreviousMonthInvoices(Long previousMonthInvoices) {
        this.previousMonthInvoices = previousMonthInvoices;
    }
}
