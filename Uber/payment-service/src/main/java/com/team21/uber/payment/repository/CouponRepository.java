package com.team21.uber.payment.repository;

import com.team21.uber.payment.dto.*;
import com.team21.uber.payment.model.Coupon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @Query("""
    SELECT new com.team21.uber.payment.dto.CouponUsageDTO(
        c.id,
        c.code,
        c.discountType,
        c.discountValue,
        CAST(COUNT(pc.id) AS integer),
        COALESCE(SUM(pc.discountApplied), 0),
        c.active,
        c.expiryDate
    )
    FROM Coupon c
    LEFT JOIN c.paymentCoupons pc
    GROUP BY c.id, c.code, c.discountType, c.discountValue, c.active, c.expiryDate
    ORDER BY COUNT(pc.id) DESC
""")
    List<CouponUsageDTO> findTopUsedCoupons(Pageable pageable);

}
