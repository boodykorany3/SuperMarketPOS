package com.pos.api.dto;

import com.pos.api.entity.AccountType;

import java.util.ArrayList;
import java.util.List;

public class AccountResponseDto {

    private Long id;
    private String code;
    private String name;
    private AccountType type;
    private Long parentId;
    private String parentCode;
    private Integer level;
    private boolean postingAllowed;
    private boolean active;
    private List<AccountResponseDto> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public boolean isPostingAllowed() {
        return postingAllowed;
    }

    public void setPostingAllowed(boolean postingAllowed) {
        this.postingAllowed = postingAllowed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<AccountResponseDto> getChildren() {
        return children;
    }

    public void setChildren(List<AccountResponseDto> children) {
        this.children = children;
    }
}
