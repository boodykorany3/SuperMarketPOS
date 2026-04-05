package com.pos.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProfitLossResponseDto {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private BigDecimal totalExpense = BigDecimal.ZERO;
    private BigDecimal netProfit = BigDecimal.ZERO;
    private List<ProfitLossLineDto> revenueLines = new ArrayList<>();
    private List<ProfitLossLineDto> expenseLines = new ArrayList<>();

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

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getNetProfit() {
        return netProfit;
    }

    public void setNetProfit(BigDecimal netProfit) {
        this.netProfit = netProfit;
    }

    public List<ProfitLossLineDto> getRevenueLines() {
        return revenueLines;
    }

    public void setRevenueLines(List<ProfitLossLineDto> revenueLines) {
        this.revenueLines = revenueLines;
    }

    public List<ProfitLossLineDto> getExpenseLines() {
        return expenseLines;
    }

    public void setExpenseLines(List<ProfitLossLineDto> expenseLines) {
        this.expenseLines = expenseLines;
    }
}
