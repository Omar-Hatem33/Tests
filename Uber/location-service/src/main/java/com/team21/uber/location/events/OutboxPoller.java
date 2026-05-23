package com.team21.uber.location.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishUnpublished() {
        List<OutboxEvent> batch = outboxRepository
                .findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        for (OutboxEvent ev : batch) {
            try {
                MessageProperties props = new MessageProperties();
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setContentEncoding("UTF-8");
                props.setHeader("__TypeId__", ev.getPayloadType());
                Message msg = new Message(ev.getPayload().getBytes(StandardCharsets.UTF_8), props);
                rabbitTemplate.send(ev.getExchange(), ev.getRoutingKey(), msg);
                ev.setPublishedAt(Instant.now());
                outboxRepository.save(ev);
            } catch (Exception e) {
                log.warn("Outbox publish failed id={} routingKey={}: {}",
                        ev.getId(), ev.getRoutingKey(), e.getMessage());
            }
        }
    }
}
