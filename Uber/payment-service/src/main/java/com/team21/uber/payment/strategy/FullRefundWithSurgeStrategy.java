package com.team21.uber.payment.strategy;

import com.team21.uber.payment.model.Payment;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Returns the full payment amount when refundSurge=true
 * and payment is within the 24-hour refund window.
 */
public class FullRefundWithSurgeStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payment payment, RefundRequest request) {
        double surgeFee = extractSurgeFee(payment);
        double refundAmount = payment.getAmount();

        return new RefundResult(refundAmount, "FULL_REFUND_WITH_SURGE");
    }

    private double extractSurgeFee(Payment payment) {
        Map<String, Object> details = payment.getTransactionDetails();
        if (details != null && details.containsKey("surgeFee")) {
            Object surgeObj = details.get("surgeFee");
            if (surgeObj instanceof Number) {
                return ((Number) surgeObj).doubleValue();
            }
        }
        // Fallback: 15% of amount as surge fee
        return payment.getAmount() * 0.15;
    }
}