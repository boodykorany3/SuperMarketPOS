package com.pos.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class JournalEntryRequestDto {

    private LocalDate entryDate;

    @NotBlank(message = "description is required")
    @Size(max = 240, message = "description must be at most 240 characters")
    private String description;

    @Size(max = 80, message = "referenceType must be at most 80 characters")
    private String referenceType;

    private Long referenceId;

    private Long branchId;

    @Valid
    @NotEmpty(message = "lines are required")
    private List<JournalLineRequestDto> lines;

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

    public List<JournalLineRequestDto> getLines() {
        return lines;
    }

    public void setLines(List<JournalLineRequestDto> lines) {
        this.lines = lines;
    }
}
