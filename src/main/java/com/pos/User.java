package com.pos;

public class User {

    private String username;
    private String password;
    private String role; // OWNER / ADMIN / CASHIER
    private Long branchId;
    private String branchCode;
    private String branchName;

    public User(String username, String password, String role) {
        this(username, password, role, null, "", "");
    }

    public User(String username, String password, String role, Long branchId, String branchCode, String branchName) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.branchId = branchId;
        this.branchCode = branchCode;
        this.branchName = branchName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public String getBranchName() {
        return branchName;
    }
}
