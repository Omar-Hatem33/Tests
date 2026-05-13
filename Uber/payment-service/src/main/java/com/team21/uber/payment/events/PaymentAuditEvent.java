package com.team21.uber.payment.events;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "payment_audit_trail")
public class PaymentAuditEvent implements MongoEvent {

    @Id
    private String id;
    private Long paymentId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details;

    public PaymentAuditEvent() {}

    public PaymentAuditEvent(Long paymentId, String action, LocalDateTime timestamp,
                             String method, Double amount, Map<String, Object> details) {
        this.paymentId = paymentId;
        this.action    = action;
        this.timestamp = timestamp;
        this.method    = method;
        this.amount    = amount;
        this.details   = details;
    }

    public PaymentAuditEvent(Long paymentId, String action,
                             LocalDateTime timestamp, Map<String, Object> details) {
        this.paymentId = paymentId;
        this.action    = action;
        this.timestamp = timestamp;
        this.details   = details;
    }

    @Override public String getId()               { return id; }
    @Override public LocalDateTime getTimestamp() { return timestamp; }
    @Override public String getAction()           { return action; }
    @Override public Map<String, Object> getDetails() { return details; }

    public Long   getPaymentId()           { return paymentId; }
    public void   setPaymentId(Long v)     { this.paymentId = v; }

    public String getMethod()              { return method; }
    public void   setMethod(String v)      { this.method = v; }

    public Double getAmount()              { return amount; }
    public void   setAmount(Double v)      { this.amount = v; }

    public void   setId(String v)          { this.id = v; }
    public void   setAction(String v)      { this.action = v; }
    public void   setTimestamp(LocalDateTime v) { this.timestamp = v; }
    public void   setDetails(Map<String, Object> v) { this.details = v; }
}