package com.team21.uber.payment.service;

import com.team21.uber.payment.cache.CacheInvalidator;
import com.team21.uber.payment.model.PaymentCoupon;
import com.team21.uber.payment.repository.PaymentCouponRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PaymentCouponService {
    private final PaymentCouponRepository repo;
    private final CacheInvalidator cacheInvalidator;


    public PaymentCouponService(PaymentCouponRepository repo, CacheInvalidator cacheInvalidator) { this.repo = repo; this.cacheInvalidator=cacheInvalidator;
   }

    public PaymentCoupon create(PaymentCoupon pc) { return repo.save(pc); }

    @Cacheable(value = "payment-coupon", key = "#id")
    public PaymentCoupon getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PaymentCoupon not found"));
    }

    public List<PaymentCoupon> getAll() { return repo.findAll(); }


    public void delete(Long id) {
        PaymentCoupon pc = getById(id);
        repo.deleteById(id);
        invalidatePaymentCouponCaches(id);
   }

    /**
     * Invalidates per §4.4.4 (DELETE /api/payment-coupons/{id}):
     *  - Entity detail:   payment-service::payment-coupon::{id}
     *  - S5-F8 details:   payment-service::S5-F8::*  (coupon list on payment may change)
     *  - S5-F9 top used:  payment-service::S5-F9::*
     *  - S5-F10/F11 analytics (M2 rule)
     */
    private void invalidatePaymentCouponCaches(Long id) {
        cacheInvalidator.evictPattern("payment-service::payment-coupon::" + id);
        cacheInvalidator.evictPattern("payment-service::S5-F8::*");
        cacheInvalidator.evictPattern("payment-service::S5-F9::*");
        cacheInvalidator.evictPattern("payment-service::S5-F10::*");
        cacheInvalidator.evictPattern("payment-service::S5-F11::*");
    }
}
