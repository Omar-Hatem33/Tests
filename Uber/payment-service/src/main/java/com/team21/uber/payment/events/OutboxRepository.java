package com.team21.uber.payment.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
