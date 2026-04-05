package com.pos.api.dto;

public class CustomerPointsRedeemResponseDto {
    private boolean redeemed;
    private int points;

    public boolean isRedeemed() {
        return redeemed;
    }

    public void setRedeemed(boolean redeemed) {
        this.redeemed = redeemed;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }
}
