package com.team21.uber.payment.strategy;

import com.team21.uber.payment.model.Payment;

/**
 * Returns zero and a "refund window expired" reason code
 * when the payment is older than 24 hours from createdAt.
 */
public class NoRefundStrategy implements RefundStrategy {

    public static final String REASON_CODE = "REFUND_WINDOW_EXPIRED";
    public static final String REASON_MESSAGE = "refund window expired";

    @Override
    public RefundResult calculateRefund(Payment payment, RefundRequest request) {
        return new RefundResult(0.0, REASON_CODE);
    }
}