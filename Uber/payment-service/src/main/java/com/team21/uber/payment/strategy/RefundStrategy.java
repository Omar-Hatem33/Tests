package com.team21.uber.payment.strategy;

import com.team21.uber.payment.model.Payment;

/**
 * Strategy interface for refund calculation.
 * Part of DP-1 Strategy Pattern — S5-F12 refund logic.
 */
public interface RefundStrategy {

    /**
     * Calculates the refund amount for a given payment and request.
     *
     * @param payment the payment to refund
     * @param request the refund request parameters
     * @return RefundResult containing the calculated amount and reason code
     */
    RefundResult calculateRefund(Payment payment, RefundRequest request);
}