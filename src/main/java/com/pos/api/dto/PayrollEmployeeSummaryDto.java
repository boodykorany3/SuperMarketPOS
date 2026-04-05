package com.pos.api.dto;

import java.math.BigDecimal;

public class PayrollEmployeeSummaryDto {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private BigDecimal monthlySalary = BigDecimal.ZERO;
    private BigDecimal totalDeduction = BigDecimal.ZERO;
    private BigDecimal netSalary = BigDecimal.ZERO;
    private Long presentDays = 0L;
    private Long lateDays = 0L;
    private Long absentDays = 0L;
    private Long noFingerprintDays = 0L;
    private Long leaveDays = 0L;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
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

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public BigDecimal getTotalDeduction() {
        return totalDeduction;
    }

    public void setTotalDeduction(BigDecimal totalDeduction) {
        this.totalDeduction = totalDeduction;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public void setNetSalary(BigDecimal netSalary) {
        this.netSalary = netSalary;
    }

    public Long getPresentDays() {
        return presentDays;
    }

    public void setPresentDays(Long presentDays) {
        this.presentDays = presentDays;
    }

    public Long getLateDays() {
        return lateDays;
    }

    public void setLateDays(Long lateDays) {
        this.lateDays = lateDays;
    }

    public Long getAbsentDays() {
        return absentDays;
    }

    public void setAbsentDays(Long absentDays) {
        this.absentDays = absentDays;
    }

    public Long getNoFingerprintDays() {
        return noFingerprintDays;
    }

    public void setNoFingerprintDays(Long noFingerprintDays) {
        this.noFingerprintDays = noFingerprintDays;
    }

    public Long getLeaveDays() {
        return leaveDays;
    }

    public void setLeaveDays(Long leaveDays) {
        this.leaveDays = leaveDays;
    }
}
