package com.pos.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BranchRequestDto {

    @NotBlank(message = "code is required")
    @Size(max = 30, message = "code must be at most 30 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    private Boolean mainBranch;

    private Boolean active;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getMainBranch() {
        return mainBranch;
    }

    public void setMainBranch(Boolean mainBranch) {
        this.mainBranch = mainBranch;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
