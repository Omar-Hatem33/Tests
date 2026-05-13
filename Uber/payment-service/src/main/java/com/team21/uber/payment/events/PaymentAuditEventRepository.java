package com.team21.uber.payment.events;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {

    List<PaymentAuditEvent> findByPaymentIdOrderByTimestampDesc(Long paymentId);

    @Query("{ 'action': { $in: ['COMPLETED', 'FAILED'] }, " +
            "'timestamp': { $gte: ?0, $lte: ?1 } }")
    List<PaymentAuditEvent> findCompletedAndFailedByTimestampBetween(
            LocalDateTime start,
            LocalDateTime end);

    @Query("{ 'timestamp': { $gte: ?0, $lte: ?1 }, 'action': { $in: ?2 } }")
    List<PaymentAuditEvent> findByTimestampBetweenAndActionIn(
            LocalDateTime start,
            LocalDateTime end,
            List<String> actions);
    List<PaymentAuditEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    boolean existsByPaymentIdAndAction(Long paymentId, String action);
}