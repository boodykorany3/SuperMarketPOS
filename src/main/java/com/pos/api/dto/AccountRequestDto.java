package com.pos.api.dto;

import com.pos.api.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AccountRequestDto {

    @Size(max = 30, message = "code must be at most 30 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 180, message = "name must be at most 180 characters")
    private String name;

    @NotNull(message = "type is required")
    private AccountType type;

    private Long parentId;

    private Boolean postingAllowed;

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

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Boolean getPostingAllowed() {
        return postingAllowed;
    }

    public void setPostingAllowed(Boolean postingAllowed) {
        this.postingAllowed = postingAllowed;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
