//package com.team21.uber.ride.service;
//
//import com.team21.uber.ride.neo4j.DriverNode;
//import com.team21.uber.ride.repository.UserNodeRepository;
//import com.team21.uber.ride.dto.DriverRecommendationDTO;
//import com.team21.uber.ride.adapter.RecommendationAdapter;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class RecommendationService {
//
//    private final UserNodeRepository repository;
//    private final RecommendationAdapter adapter;
//
//    public RecommendationService(UserNodeRepository repository,
//                                 RecommendationAdapter adapter) {
//        this.repository = repository;
//        this.adapter = adapter;
//    }
//
//    public List<DriverRecommendationDTO> getRecommendations(Long userId) {
//        List<DriverNode> nodes = repository.recommendDrivers(userId);
//
//        return nodes.stream()
//                .map(adapter::toDTO)
//                .collect(Collectors.toList());
//    }
//}