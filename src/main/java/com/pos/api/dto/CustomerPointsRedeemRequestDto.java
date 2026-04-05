package com.pos.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CustomerPointsRedeemRequestDto {

    @NotBlank(message = "phone is required")
    private String phone;

    @Min(value = 1, message = "cost must be at least 1")
    private int cost;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }
}
