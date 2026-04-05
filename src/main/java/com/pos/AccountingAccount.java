package com.pos;

import java.util.ArrayList;
import java.util.List;

public class AccountingAccount {
    private Long id;
    private String code;
    private String name;
    private String type;
    private Long parentId;
    private String parentCode;
    private Integer level;
    private boolean postingAllowed;
    private boolean active;
    private List<AccountingAccount> children = new ArrayList<>();

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public List<AccountingAccount> getChildren() {
        return children;
    }

    public void setChildren(List<AccountingAccount> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }

    public String getDisplayName() {
        int depth = level == null ? 0 : Math.max(0, level);
        String prefix = "  ".repeat(depth);
        return prefix + (name == null ? "" : name);
    }
}
