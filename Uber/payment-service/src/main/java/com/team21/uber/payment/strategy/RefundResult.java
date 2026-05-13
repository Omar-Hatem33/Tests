package com.team21.uber.payment.strategy;

/**
 * Value object returned by RefundStrategy.calculateRefund().
 * Contains the calculated refund amount and a reason code.
 */
public class RefundResult {

    private final double amount;
    private final String reasonCode;

    public RefundResult(double amount, String reasonCode) {
        this.amount = amount;
        this.reasonCode = reasonCode;
    }

    public double getAmount() {
        return amount;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String toString() {
        return "RefundResult{" +
                "amount=" + amount +
                ", reasonCode='" + reasonCode + '\'' +
                '}';
    }
}