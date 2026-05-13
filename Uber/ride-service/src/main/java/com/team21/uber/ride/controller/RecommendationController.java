//package com.team21.uber.ride.controller;
//
//import com.team21.uber.ride.dto.DriverRecommendationDTO;
//import com.team21.uber.ride.service.RecommendationService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/rides")
//public class RecommendationController {
//
//    private final RecommendationService service;
//
//    public RecommendationController(RecommendationService service) {
//        this.service = service;
//    }
//
//    @GetMapping("/users/{userId}/recommendations")
//    public ResponseEntity<List<DriverRecommendationDTO>> getRecommendations(
//            @PathVariable Long userId) {
//
//        return ResponseEntity.ok(service.getRecommendations(userId));
//    }
//}