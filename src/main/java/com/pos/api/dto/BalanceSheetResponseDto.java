package com.pos.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BalanceSheetResponseDto {

    private LocalDate asOfDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private BigDecimal totalAssets = BigDecimal.ZERO;
    private BigDecimal totalLiabilities = BigDecimal.ZERO;
    private BigDecimal totalEquity = BigDecimal.ZERO;
    private BigDecimal totalLiabilitiesAndEquity = BigDecimal.ZERO;
    private BigDecimal difference = BigDecimal.ZERO;
    private List<BalanceSheetLineDto> assetLines = new ArrayList<>();
    private List<BalanceSheetLineDto> liabilityLines = new ArrayList<>();
    private List<BalanceSheetLineDto> equityLines = new ArrayList<>();

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
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

    public BigDecimal getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(BigDecimal totalAssets) {
        this.totalAssets = totalAssets;
    }

    public BigDecimal getTotalLiabilities() {
        return totalLiabilities;
    }

    public void setTotalLiabilities(BigDecimal totalLiabilities) {
        this.totalLiabilities = totalLiabilities;
    }

    public BigDecimal getTotalEquity() {
        return totalEquity;
    }

    public void setTotalEquity(BigDecimal totalEquity) {
        this.totalEquity = totalEquity;
    }

    public BigDecimal getTotalLiabilitiesAndEquity() {
        return totalLiabilitiesAndEquity;
    }

    public void setTotalLiabilitiesAndEquity(BigDecimal totalLiabilitiesAndEquity) {
        this.totalLiabilitiesAndEquity = totalLiabilitiesAndEquity;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }

    public List<BalanceSheetLineDto> getAssetLines() {
        return assetLines;
    }

    public void setAssetLines(List<BalanceSheetLineDto> assetLines) {
        this.assetLines = assetLines;
    }

    public List<BalanceSheetLineDto> getLiabilityLines() {
        return liabilityLines;
    }

    public void setLiabilityLines(List<BalanceSheetLineDto> liabilityLines) {
        this.liabilityLines = liabilityLines;
    }

    public List<BalanceSheetLineDto> getEquityLines() {
        return equityLines;
    }

    public void setEquityLines(List<BalanceSheetLineDto> equityLines) {
        this.equityLines = equityLines;
    }
}
