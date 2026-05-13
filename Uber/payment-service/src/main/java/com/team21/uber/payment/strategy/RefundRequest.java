package com.team21.uber.payment.strategy;

/**
 * Value object carrying the refund request parameters.
 * Used by RefundStrategy.calculateRefund().
 */
public class RefundRequest {

    private final String reason;
    private final boolean refundSurge;

    public RefundRequest(String reason, boolean refundSurge) {
        this.reason = reason;
        this.refundSurge = refundSurge;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRefundSurge() {
        return refundSurge;
    }

    @Override
    public String toString() {
        return "RefundRequest{" +
                "reason='" + reason + '\'' +
                ", refundSurge=" + refundSurge +
                '}';
    }
}