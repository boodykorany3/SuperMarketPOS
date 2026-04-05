package com.pos.api.dto;

import com.pos.api.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserRequestDto {

    @NotBlank(message = "username is required")
    @Size(max = 60, message = "username must be at most 60 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 4, max = 100, message = "password must be between 4 and 100 characters")
    private String password;

    @NotNull(message = "role is required")
    private Role role;

    @NotNull(message = "branchId is required")
    private Long branchId;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
}
