package com.team21.uber.payment.dto;

/**
 * Request DTO for the S5-F12 surge-adjusted refund endpoint.
 */
public class RefundRequestDTO {

    private String reason;
    private boolean refundSurge;

    public RefundRequestDTO() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isRefundSurge() {
        return refundSurge;
    }

    public void setRefundSurge(boolean refundSurge) {
        this.refundSurge = refundSurge;
    }
}