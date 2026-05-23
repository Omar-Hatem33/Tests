package com.team21.uber.location.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_unpublished", columnList = "published_at")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange", nullable = false, length = 128)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 128)
    private String routingKey;

    @Column(name = "payload_type", nullable = false, length = 256)
    private String payloadType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent() {}

    public OutboxEvent(String exchange, String routingKey, String payloadType, String payload) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadType() { return payloadType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
