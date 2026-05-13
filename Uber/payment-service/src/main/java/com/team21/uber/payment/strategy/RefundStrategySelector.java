package com.team21.uber.payment.strategy;

import com.team21.uber.payment.model.Payment;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Selects the appropriate refund strategy based on refundSurge flag
 * and payment age (24-hour refund window).
 *
 * The service calls: selector.select(payment, request).calculateRefund(payment, request)
 */
public class RefundStrategySelector {

    private static final long REFUND_WINDOW_HOURS = 24;

    /**
     * Selects a refund strategy based on payment age and refund request parameters.
     *
     * @param payment the payment to evaluate
     * @param request the refund request containing refundSurge flag
     * @return the appropriate RefundStrategy implementation
     */
    public RefundStrategy select(Payment payment, RefundRequest request) {
        if (isRefundWindowExpired(payment)) {
            return new NoRefundStrategy();
        }

        if (request.isRefundSurge()) {
            return new FullRefundWithSurgeStrategy();
        } else {
            return new BaseFareOnlyRefundStrategy();
        }
    }

    private boolean isRefundWindowExpired(Payment payment) {
        if (payment.getCreatedAt() == null) {
            return false;
        }
        LocalDateTime createdAt = payment.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(createdAt, now);
        return duration.toHours() >= REFUND_WINDOW_HOURS;
    }
}