package com.pos.api.dto;

import jakarta.validation.constraints.Size;

public class AttendanceWaiveRequestDto {

    @Size(max = 240, message = "reason must be at most 240 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
