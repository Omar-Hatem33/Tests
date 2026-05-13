package com.team21.uber.payment.adapter;

import com.team21.uber.payment.events.PaymentAuditEvent;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    public PaymentAuditEvent adapt(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Cannot adapt a null Document");
        }

        PaymentAuditEvent event = new PaymentAuditEvent();
        Object rawId = document.get("_id");
        if (rawId != null) {
            event.setId(rawId.toString());
        }

        Object rawPaymentId = document.get("paymentId");
        if (rawPaymentId instanceof Number) {
            event.setPaymentId(((Number) rawPaymentId).longValue());
        }

        event.setAction(document.getString("action"));

        Object rawTs = document.get("timestamp");
        if (rawTs instanceof Date date) {
            event.setTimestamp(
                    date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else if (rawTs instanceof LocalDateTime ldt) {
            event.setTimestamp(ldt);
        }

        event.setMethod(document.getString("method"));
        Object rawAmount = document.get("amount");
        if (rawAmount instanceof Number) {
            event.setAmount(((Number) rawAmount).doubleValue());
        }

        Object rawDetails = document.get("details");
        if (rawDetails instanceof Document detailsDoc) {
            Map<String, Object> details = new HashMap<>(detailsDoc);
            event.setDetails(details);
        } else {
            event.setDetails(new HashMap<>());
        }

        return event;
    }
}