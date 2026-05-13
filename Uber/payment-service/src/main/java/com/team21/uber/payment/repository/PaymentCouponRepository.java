package com.team21.uber.payment.repository;

import com.team21.uber.payment.model.PaymentCoupon;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentCouponRepository extends JpaRepository<PaymentCoupon, Long> {

    // for dup check
    boolean existsByPaymentIdAndCouponId(Long paymentId, Long couponId);
}
