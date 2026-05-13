package com.team21.uber.driver.repository;

import com.team21.uber.driver.model.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {
}

