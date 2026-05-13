package com.team21.uber.payment.service;

import com.team21.uber.payment.dto.*;
import com.team21.uber.payment.model.Coupon;
import com.team21.uber.payment.model.DiscountType;
import com.team21.uber.payment.repository.CouponRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team21.uber.payment.cache.CacheInvalidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CouponService {
    private final CouponRepository couponRepository;
    private final CacheInvalidator cacheInvalidator;


    public CouponService(CouponRepository couponRepository, CacheInvalidator cacheInvalidator) {
        this.couponRepository = couponRepository;
        this.cacheInvalidator=cacheInvalidator;


    }

    public Coupon createCoupon(Coupon coupon) { return couponRepository.save(coupon); }


    @Cacheable(value = "coupon", key = "#id")
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    }

    public List<Coupon> getAllCoupons() { return couponRepository.findAll(); }

    public Coupon updateCoupon(Long id, Coupon updated) {
        Coupon existing = getCouponById(id);
        existing.setCode(updated.getCode());
        existing.setDiscountType(updated.getDiscountType());
        existing.setDiscountValue(updated.getDiscountValue());
        existing.setMaxUses(updated.getMaxUses());
        existing.setExpiryDate(updated.getExpiryDate());
        existing.setActive(updated.getActive());
        existing.setMetadata(updated.getMetadata());
        Coupon saved = couponRepository.save(existing);
        invalidateCouponCaches(id);   // PUT /api/coupons/{id}
        return saved;


    }


    public void deleteCoupon(Long id) {
        getCouponById(id);
        couponRepository.deleteById(id);
        invalidateCouponCaches(id);

    }

    //S5-F9
    @Cacheable(value = "S5-F9", key = "#limit")
    public List<CouponUsageDTO> getTopUsedCoupons(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return couponRepository.findTopUsedCoupons(pageable);
    }


    // ─── Invalidation helper ──────────────────────────────────────────────────

    /**
     * Called by every write that mutates a Coupon entity.
     *
     * Invalidates per §4.4.4:
     *  - Entity detail:          payment-service::coupon::{id}
     *  - S5-F5 uses coupon data: payment-service::S5-F8::*  (PaymentDetailsDTO embeds coupon info)
     *  - S5-F9 top used report:  payment-service::S5-F9::*
     *  - S5-F10/F11 analytics:   payment-service::S5-F10::*  /  S5-F11::*  (M2 rule)
     */
    private void invalidateCouponCaches(Long id) {
        cacheInvalidator.evictPattern("payment-service::coupon::" + id);
        cacheInvalidator.evictPattern("payment-service::S5-F8::*");
        cacheInvalidator.evictPattern("payment-service::S5-F9::*");
        cacheInvalidator.evictPattern("payment-service::S5-F10::*");
        cacheInvalidator.evictPattern("payment-service::S5-F11::*");
    }

}
