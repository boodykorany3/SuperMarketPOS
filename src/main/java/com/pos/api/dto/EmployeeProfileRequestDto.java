package com.pos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class EmployeeProfileRequestDto {

    @NotBlank(message = "employeeCode is required")
    @Size(max = 40, message = "employeeCode must be at most 40 characters")
    private String employeeCode;

    @NotBlank(message = "fullName is required")
    @Size(max = 140, message = "fullName must be at most 140 characters")
    private String fullName;

    @NotNull(message = "branchId is required")
    private Long branchId;

    @NotNull(message = "monthlySalary is required")
    @DecimalMin(value = "0.00", message = "monthlySalary must be >= 0")
    private BigDecimal monthlySalary;

    private Boolean active;

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
