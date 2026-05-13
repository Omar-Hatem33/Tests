package com.team21.uber.driver.controller;

import com.team21.uber.driver.model.DriverDocument;
import com.team21.uber.driver.service.DriverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DriverDocumentController {

    private final DriverService driverService;

    public DriverDocumentController(DriverService driverService) {
        this.driverService = driverService;
    }

    // CREATE DOCUMENT
    @PostMapping("/driver/{driverId}")
    public ResponseEntity<DriverDocument> createDocument(@PathVariable Long driverId,
                                                         @RequestBody DriverDocument document) {
        return ResponseEntity.ok(driverService.createDocument(driverId, document));
    }

    // GET ALL DOCUMENTS
    @GetMapping
    public ResponseEntity<List<DriverDocument>> getAllDocuments() {
        return ResponseEntity.ok(driverService.getAllDocuments());
    }

    // GET DOCUMENT BY ID
    @GetMapping("/{id}")
    public ResponseEntity<DriverDocument> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.getDocumentByIdOrThrow(id));
    }

    // UPDATE DOCUMENT
    @PutMapping("/{id}")
    public ResponseEntity<DriverDocument> updateDocument(@PathVariable Long id,
                                                         @RequestBody DriverDocument document) {
        return ResponseEntity.ok(driverService.updateDocument(id, document));
    }

    // DELETE DOCUMENT
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        driverService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}