package com.team21.uber.driver.repository;

import com.team21.uber.driver.model.DriverSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DriverSearchRepository extends ElasticsearchRepository<DriverSearchDocument, String> {
    // Dynamic multi-filter search is handled in DriverService via ElasticsearchOperations
    // to support optional vehicleType, status, and rating range filters alongside
    // full-text matching on name and description.
}
