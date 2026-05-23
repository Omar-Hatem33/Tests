//////package com.team21.uber.user.service;
//////
//////import com.team21.uber.user.model.Role;
//////import com.team21.uber.user.model.User;
//////import com.team21.uber.user.repository.UserRepository;
//////import org.springframework.stereotype.Service;
//////import java.util.ArrayList;
//////import java.util.List;
//////import com.team21.uber.user.model.SavedAddress;
//////import com.team21.uber.user.dto.TopRiderDTO;
//////import com.team21.uber.user.dto.UserProfileDTO;
//////import com.team21.uber.user.dto.SavedAddressDTO;
//////import com.team21.uber.user.model.UserStatus;
//////import com.team21.uber.user.repository.SavedAddressRepository;
//////import com.team21.uber.user.repository.UserRepository;
//////import com.team21.uber.user.model.User;
//////import com.team21.uber.user.repository.SavedAddressRepository;
//////import jakarta.transaction.Transactional;
//////import org.springframework.http.HttpStatus;
//////import org.springframework.http.HttpStatusCode;
//////import org.springframework.stereotype.Service;
//////import org.springframework.web.server.ResponseStatusException;
//////import com.team21.uber.user.dto.UserRideSummaryDTO;
//////import java.time.LocalDateTime;
//////import java.util.stream.Collectors;
//////
//////import java.util.List;
//////import java.util.ArrayList;
//////import java.util.List;
//////import java.util.Map;
//////import java.util.Optional;
//////
//////@Service
//////public class UserService {
//////    private final UserRepository userRepository;
//////    private final SavedAddressRepository savedAddressRepository;
//////
//////    public UserService(UserRepository repo,  SavedAddressRepository savedAddressRepository) {
//////        this.userRepository = repo;
//////        this.savedAddressRepository = savedAddressRepository;
//////    }
//////
//////
//////    public List<User> searchUsers(String name, String email, Role role) {
//////        if (isEmptyOrSpaces(name))  name = null;
//////        if (isEmptyOrSpaces(email)) email = null;
//////
//////        if (name == null && email == null && role == null) {
//////            return userRepository.findAll();
//////        }
//////        String nameinlowercase;
//////        String emailinlowercase;
//////        if (name == null){
//////            nameinlowercase = null;
//////        }
//////        else{
//////            nameinlowercase = "%"+ name.toLowerCase()+"%";
//////        }
//////        if (email == null){
//////            emailinlowercase = null;
//////        }
//////        else{
//////            emailinlowercase = "%"+ email.toLowerCase()+"%";
//////        }
//////
//////        return userRepository.searchUsers(nameinlowercase, emailinlowercase, role);
//////    }
//////
//////    private boolean isEmptyOrSpaces(String value) {
//////        if (value == null) return true;
//////        return value.trim().length() == 0;
//////    }
//////
//////    // FIX: Added missing rideRepository parameter to the constructor
//////
//////
//////    // ── CRUD ────────────────────────────────────────────────────────────────
//////
//////    public User createUser(User user) {
//////        if (userRepository.existsByEmail(user.getEmail())) {
//////            throw new ResponseStatusException(
//////                    HttpStatus.BAD_REQUEST,
//////                    "Email already exists"
//////            );
//////        }
//////
//////        if (userRepository.existsByPhone(user.getPhone())) {
//////            throw new ResponseStatusException(
//////                    HttpStatus.BAD_REQUEST,
//////                    "Phone already exists"
//////            );
//////        }
//////
//////        return userRepository.save(user);
//////    }
//////
//////    public User getUserById(Long id) {
//////        return userRepository.findById(id)
//////                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
//////    }
//////
//////    public List<User> getAllUsers() {
//////        return userRepository.findAll();
//////    }
//////
//////    public User updateUser(Long id, User updated) {
//////        User user = getUserById(id);
//////        user.setName(updated.getName());
//////        user.setEmail(updated.getEmail());
//////        user.setPhone(updated.getPhone());
//////        user.setRole(updated.getRole());
//////        user.setStatus(updated.getStatus());
//////        user.setPreferences(updated.getPreferences());
//////        user.setEmail(updated.getEmail());
//////        user.setPhone(updated.getPhone());
//////        return userRepository.save(user);
//////    }
//////
//////    public void deleteUser(Long id) {
//////        userRepository.deleteById(id);
//////    }
//////
//////    // S1-F8: Get User Profile with Addresses
//////    public UserProfileDTO getUserProfile(Long userId) {
//////        User user = userRepository.findById(userId)
//////                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
//////
//////        List<SavedAddressDTO> addressDTOs = user.getSavedAddresses()
//////                .stream()
//////                .map(addr -> new SavedAddressDTO(
//////                        addr.getId(),
//////                        addr.getLabel(),
//////                        addr.getAddress(),
//////                        addr.getLatitude(),
//////                        addr.getLongitude(),
//////                        addr.getIsDefault(),
//////                        addr.getMetadata()
//////                ))
//////                .collect(Collectors.toList());
//////
//////        return new UserProfileDTO(
//////                user.getId(),
//////                user.getName(),
//////                user.getEmail(),
//////                user.getPhone(),
//////                user.getPreferences(),
//////                addressDTOs,
//////                addressDTOs.size()
//////        );
//////    }
//////
//////    @Transactional
//////    public User deactivateUser(Long id) {
//////        User user = userRepository.findById(id)
//////                .orElseThrow(() -> new ResponseStatusException(
//////                        HttpStatus.NOT_FOUND, "User not found"
//////                ));
//////
//////        Long activeRideCount = userRepository.countActiveRidesByUserId(id);
//////
//////        if (activeRideCount != null && activeRideCount > 0) {
//////            throw new ResponseStatusException(
//////                    HttpStatus.BAD_REQUEST,
//////                    "User has active rides"
//////            );
//////        }
//////
//////        user.setStatus(UserStatus.DEACTIVATED);
//////        return userRepository.save(user);
//////    }
//////
//////    public List<User> filterUsersByPref(String key, Object value){
//////        if(key==null || value == null || key.isBlank())
//////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Key or value is blank");
//////
//////        List<User> allUsers = userRepository.findAll();
//////        List<User> filtered= allUsers.stream().filter((user) -> {
//////            Map<String, Object> prefs = user.getPreferences();
//////
//////            if (prefs == null || !prefs.containsKey(key)) {
//////                return false;
//////            }
//////            Object userValue = prefs.get(key);
//////            return userValue.toString().equals(value.toString());
//////        }).toList();
//////
//////        return filtered;
//////    }
//////    public List<User> getUsersByLanguageAndMinRides(String lang, Integer minRides) {
//////        if (lang == null || lang.trim().isEmpty()) {
//////            throw new ResponseStatusException(
//////                    HttpStatus.BAD_REQUEST,
//////                    "lang must not be blank"
//////            );
//////        }
//////
//////        if (minRides == null) {
//////            minRides = 0;
//////        }
//////
//////        return userRepository.findUsersByLanguageAndMinCompletedRides(
//////                lang.trim(),
//////                minRides
//////        );
//////    }
//////
//////
//////    public UserRideSummaryDTO getUserRideSummary(Long userId) {
//////        User user = userRepository.findById(userId)
//////                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
//////
//////        List<Object[]> results = userRepository.getRideSummaryByUserId(userId);
//////
//////        if (results == null || results.isEmpty()) {
//////            return new UserRideSummaryDTO(user.getId(), user.getName(), 0, 0, 0, 0.0, 0.0);
//////        }
//////
//////        Object[] result = results.get(0);
//////
//////        return new UserRideSummaryDTO(
//////                ((Number) result[0]).longValue(),
//////                (String) result[1],
//////                ((Number) result[2]).intValue(),
//////                ((Number) result[3]).intValue(),
//////                ((Number) result[4]).intValue(),
//////                ((Number) result[5]).doubleValue(),
//////                ((Number) result[6]).doubleValue()
//////        );}
//////
//////    public List<TopRiderDTO> getTopRiders(LocalDateTime startDate, LocalDateTime endDate, int limit) {
//////
//////        if (startDate.isAfter(endDate)) {
//////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
//////                    "startDate must not be after endDate");
//////        }
//////
//////        List<Object[]> results = userRepository.findTopRidersBySpending(startDate, endDate, limit);
//////
//////        return results.stream().map(row -> new TopRiderDTO(
//////                ((Number) row[0]).longValue(),    // userId
//////                (String) row[1],                  // name
//////                ((Number) row[2]).doubleValue(),   // totalSpent
//////                ((Number) row[3]).longValue()      // rideCount
//////        )).collect(Collectors.toList());
//////    }
//////    @Transactional
//////    public User setDefaultSavedAddress(Long userId, Long addressId){
//////        SavedAddress address = savedAddressRepository.findById(addressId)
//////                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not Found"));
//////
//////        User user = userRepository.findById(userId)
//////                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not Found"));
//////
//////        Long addressOwnerId = address.getUser().getId();
//////        if(!addressOwnerId.equals(userId)){
//////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST," Address doesn’t belong to this User");
//////        }
//////
//////        List<SavedAddress> allSavedAddresses = user.getSavedAddresses();
//////        allSavedAddresses.forEach(addr -> addr.setDefault(addr.getId().equals(addressId)));
//////        savedAddressRepository.saveAll(allSavedAddresses);
//////
//////        return user;
//////    }
//////
//////    public User updateUserPrefs(Long id, Map<String, Object> newPrefs){
//////        User user = userRepository.findById(id)
//////                .orElseThrow(
//////                        () -> new ResponseStatusException(
//////                                HttpStatus.NOT_FOUND, "User not found"));
//////
//////        Map<String, Object> preference = user.getPreferences();
//////        preference.putAll(newPrefs);
//////        user.setPreferences(preference);
//////        userRepository.save(user);
//////        return user;
//////    }
//////
//////    public User save(User user) {
//////        return userRepository.save(user);
//////    }
//////
//////}
////package com.team21.uber.user.service;
////
////import com.team21.uber.user.cache.CacheInvalidator;
////import com.team21.uber.user.dto.*;
////import com.team21.uber.user.model.*;
////import com.team21.uber.user.repository.*;
////
////import jakarta.transaction.Transactional;
////import org.springframework.cache.annotation.Cacheable;
////import org.springframework.http.HttpStatus;
////import org.springframework.stereotype.Service;
////import org.springframework.web.server.ResponseStatusException;
////
////import java.time.LocalDateTime;
////import java.util.*;
////import java.util.stream.Collectors;
////
////@Service
////public class UserService {
////
////    private final UserRepository userRepository;
////    private final SavedAddressRepository savedAddressRepository;
////    private final CacheInvalidator cacheInvalidator;
////
////    public UserService(UserRepository repo,
////                       SavedAddressRepository savedAddressRepository,
////                       CacheInvalidator cacheInvalidator) {
////        this.userRepository = repo;
////        this.savedAddressRepository = savedAddressRepository;
////        this.cacheInvalidator = cacheInvalidator;
////    }
////
////    // =========================================================
////    // 🔍 S1-F1 SEARCH USERS (CACHE - 5 MIN)
////    // =========================================================
////    @Cacheable(value = "search-5m",
////            key = "T(java.util.Objects).hash(#name, #email, #role)")
////    public List<User> searchUsers(String name, String email, Role role) {
////
////        if (name != null && name.isBlank()) name = null;
////        if (email != null && email.isBlank()) email = null;
////
////        if (name == null && email == null && role == null) {
////            return userRepository.findAll();
////        }
////
////        return userRepository.searchUsers(
////                name == null ? null : "%" + name.toLowerCase() + "%",
////                email == null ? null : "%" + email.toLowerCase() + "%",
////                role
////        );
////    }
////
////    // =========================================================
////    // ➕ CREATE USER (NO CACHE, BUT INVALIDATES RELATED CACHE)
////    // =========================================================
////    public User createUser(User user) {
////
////        User saved = userRepository.save(user);
////        invalidateUserCaches(saved.getId());
////
////        return saved;
////    }
////
////    // =========================================================
////    // 🔍 GET USER BY ID (CACHE - 15 MIN)
////    // =========================================================
////    @Cacheable(value = "entity-15m", key = "#id")
////    public User getUserById(Long id) {
////
////        return userRepository.findById(id)
////                .orElseThrow(() ->
////                        new RuntimeException("User not found with id: " + id));
////    }
////
////    // =========================================================
////    // 📋 GET ALL USERS (NO CACHE PER SPEC)
////    // =========================================================
////    public List<User> getAllUsers() {
////        return userRepository.findAll();
////    }
////
////    // =========================================================
////    // ✏️ UPDATE USER (INVALIDATES CACHE)
////    // =========================================================
////    public User updateUser(Long id, User updated) {
////
////        User user = userRepository.findById(id)
////                .orElseThrow(() ->
////                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
////
////        user.setName(updated.getName());
////        user.setEmail(updated.getEmail());
////        user.setPhone(updated.getPhone());
////        user.setRole(updated.getRole());
////        user.setStatus(updated.getStatus());
////        user.setPreferences(updated.getPreferences());
////
////        User saved = userRepository.save(user);
////
////        invalidateUserCaches(id);
////
////        return saved;
////    }
////
////    // =========================================================
////    // ❌ DELETE USER (INVALIDATES CACHE)
////    // =========================================================
////    public void deleteUser(Long id) {
////
////        userRepository.deleteById(id);
////        invalidateUserCaches(id);
////    }
////
////    // =========================================================
////    // 🔍 S1-F8 USER PROFILE (CACHE - 15 MIN RELATIONSHIP)
////    // =========================================================
////    @Cacheable(value = "relationship-15m", key = "#userId")
////    public UserProfileDTO getUserProfile(Long userId) {
////
////        User user = userRepository.findById(userId)
////                .orElseThrow(() ->
////                        new RuntimeException("User not found"));
////
////        List<SavedAddressDTO> addressDTOs = user.getSavedAddresses()
////                .stream()
////                .map(addr -> new SavedAddressDTO(
////                        addr.getId(),
////                        addr.getLabel(),
////                        addr.getAddress(),
////                        addr.getLatitude(),
////                        addr.getLongitude(),
////                        addr.getIsDefault(),
////                        addr.getMetadata()
////                ))
////                .collect(Collectors.toList());
////
////        return new UserProfileDTO(
////                user.getId(),
////                user.getName(),
////                user.getEmail(),
////                user.getPhone(),
////                user.getPreferences(),
////                addressDTOs,
////                addressDTOs.size()
////        );
////    }
////
////    // =========================================================
////    // 🚫 DEACTIVATE USER (INVALIDATES CACHE)
////    // =========================================================
////    @Transactional
////    public User deactivateUser(Long id) {
////
////        User user = userRepository.findById(id)
////                .orElseThrow(() ->
////                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
////
////        Long activeRideCount = userRepository.countActiveRidesByUserId(id);
////
////        if (activeRideCount != null && activeRideCount > 0) {
////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
////                    "User has active rides");
////        }
////
////        user.setStatus(UserStatus.DEACTIVATED);
////
////        User saved = userRepository.save(user);
////
////        invalidateUserCaches(id);
////
////        return saved;
////    }
////
////    // =========================================================
////    // 🔍 S1-F5 FILTER USERS BY PREF (CACHE)
////    // =========================================================
////    @Cacheable(value = "jsonb-5m",
////            key = "T(java.util.Objects).hash(#key, #value)")
////    public List<User> filterUsersByPref(String key, Object value) {
////
////        if (key == null || key.isBlank() || value == null) {
////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
////                    "Key or value invalid");
////        }
////
////        return userRepository.findAll().stream()
////                .filter(user -> {
////                    Map<String, Object> prefs = user.getPreferences();
////                    return prefs != null &&
////                            value.equals(prefs.get(key));
////                })
////                .toList();
////    }
////
////    // =========================================================
////    // 🔍 S1-F5 LANGUAGE FILTER (CACHE)
////    // =========================================================
////    @Cacheable(value = "jsonb-5m",
////            key = "T(java.util.Objects).hash(#lang, #minRides)")
////    public List<User> getUsersByLanguageAndMinRides(String lang, Integer minRides) {
////
////        if (lang == null || lang.isBlank()) {
////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
////                    "Language required");
////        }
////
////        if (minRides == null) minRides = 0;
////
////        return userRepository.findUsersByLanguageAndMinCompletedRides(lang, minRides);
////    }
////
////    // =========================================================
////    // 🔍 S1-F3 RIDE SUMMARY (CACHE - DTO 10 MIN)
////    // =========================================================
////    @Cacheable(value = "dto-10m", key = "#userId")
////    public UserRideSummaryDTO getUserRideSummary(Long userId) {
////
////        User user = userRepository.findById(userId)
////                .orElseThrow(() ->
////                        new RuntimeException("User not found"));
////
////        List<Object[]> results = userRepository.getRideSummaryByUserId(userId);
////
////        if (results == null || results.isEmpty()) {
////            return new UserRideSummaryDTO(user.getId(), user.getName(),
////                    0, 0, 0, 0.0, 0.0);
////        }
////
////        Object[] r = results.get(0);
////
////        return new UserRideSummaryDTO(
////                ((Number) r[0]).longValue(),
////                (String) r[1],
////                ((Number) r[2]).intValue(),
////                ((Number) r[3]).intValue(),
////                ((Number) r[4]).intValue(),
////                ((Number) r[5]).doubleValue(),
////                ((Number) r[6]).doubleValue()
////        );
////    }
////
////    // =========================================================
////    // 🔍 S1-F6 TOP RIDERS (CACHE - REPORT 10 MIN)
////    // =========================================================
////    @Cacheable(value = "report-10m",
////            key = "T(java.util.Objects).hash(#startDate, #endDate, #limit)")
////    public List<TopRiderDTO> getTopRiders(LocalDateTime startDate,
////                                          LocalDateTime endDate,
////                                          int limit) {
////
////        if (startDate.isAfter(endDate)) {
////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
////                    "Invalid date range");
////        }
////
////        return userRepository.findTopRidersBySpending(startDate, endDate, limit)
////                .stream()
////                .map(row -> new TopRiderDTO(
////                        ((Number) row[0]).longValue(),
////                        (String) row[1],
////                        ((Number) row[2]).doubleValue(),
////                        ((Number) row[3]).longValue()
////                ))
////                .collect(Collectors.toList());
////    }
////
////    // =========================================================
////    // 🏠 SET DEFAULT ADDRESS (INVALIDATES CACHE)
////    // =========================================================
////    @Transactional
////    public User setDefaultSavedAddress(Long userId, Long addressId) {
////
////        SavedAddress address = savedAddressRepository.findById(addressId)
////                .orElseThrow(() ->
////                        new ResponseStatusException(HttpStatus.NOT_FOUND,
////                                "Address not found"));
////
////        User user = userRepository.findById(userId)
////                .orElseThrow(() ->
////                        new ResponseStatusException(HttpStatus.NOT_FOUND,
////                                "User not found"));
////
////        if (!address.getUser().getId().equals(userId)) {
////            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
////                    "Address does not belong to user");
////        }
////
////        List<SavedAddress> all = user.getSavedAddresses();
////
////        all.forEach(addr ->
////                addr.setDefault(addr.getId().equals(addressId)));
////
////        savedAddressRepository.saveAll(all);
////
////        invalidateUserCaches(userId);
////
////        return user;
////    }
////
////    // =========================================================
////    // 🔧 UPDATE PREFS (INVALIDATES CACHE)
////    // =========================================================
////    public User updateUserPrefs(Long id, Map<String, Object> newPrefs) {
////
////        User user = userRepository.findById(id)
////                .orElseThrow();
////
////        user.getPreferences().putAll(newPrefs);
////
////        User saved = userRepository.save(user);
////
////        invalidateUserCaches(id);
////
////        return saved;
////    }
////
////    // =========================================================
////    // 🔥 CENTRAL CACHE INVALIDATION (M2 CORE REQUIREMENT)
////    // =========================================================
////    private void invalidateUserCaches(Long userId) {
////
////        cacheInvalidator.evictPattern("user-service::entity-15m::" + userId);
////        cacheInvalidator.evictPattern("user-service::relationship-15m::" + userId);
////
////        cacheInvalidator.evictPattern("user-service::search-5m::*");
////        cacheInvalidator.evictPattern("user-service::jsonb-5m::*");
////        cacheInvalidator.evictPattern("user-service::dto-10m::*");
////        cacheInvalidator.evictPattern("user-service::report-10m::*");
////    }
////
////    // optional helper
////    public User save(User user) {
////        return userRepository.save(user);
////    }
////}
//package com.team21.uber.user.service;
//
//import com.team21.uber.user.cache.CacheInvalidator;
//import com.team21.uber.user.model.*;
//import com.team21.uber.user.repository.*;
//import com.team21.uber.user.dto.*;
//
//import jakarta.transaction.Transactional;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Service;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class UserService {
//
//    private final UserRepository userRepository;
//    private final SavedAddressRepository savedAddressRepository;
//    private final CacheInvalidator cacheInvalidator;
//
//    public UserService(UserRepository repo,
//                       SavedAddressRepository savedAddressRepository,
//                       CacheInvalidator cacheInvalidator) {
//        this.userRepository = repo;
//        this.savedAddressRepository = savedAddressRepository;
//        this.cacheInvalidator = cacheInvalidator;
//    }
//
//    // =========================================================
//    // 🔍 S1-F1 SEARCH USERS (5 min)
//    // =========================================================
//    @Cacheable(value = "S1-F1", key = "T(java.util.Objects).hash(#name, #email, #role)")
//    public List<User> searchUsers(String name, String email, Role role) {
//
//        if (name != null && name.isBlank()) name = null;
//        if (email != null && email.isBlank()) email = null;
//
//        if (name == null && email == null && role == null) {
//            return userRepository.findAll();
//        }
//
//        return userRepository.searchUsers(
//                name == null ? null : "%" + name.toLowerCase() + "%",
//                email == null ? null : "%" + email.toLowerCase() + "%",
//                role
//        );
//    }
//
//    // =========================================================
//    // ➕ CREATE USER
//    // =========================================================
//    public User createUser(User user) {
//
//        User saved = userRepository.save(user);
//
//        invalidateUserCaches(saved.getId());
//
//        return saved;
//    }
//
//    // =========================================================
//    // 🔍 GET USER BY ID (entity cache)
//    // =========================================================
//    @Cacheable(value = "user", key = "#id")
//    public User getUserById(Long id) {
//
//        return userRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//    }
//
//    // =========================================================
//    // 📋 GET ALL USERS (NOT CACHED)
//    // =========================================================
//    public List<User> getAllUsers() {
//        return userRepository.findAll();
//    }
//
//    // =========================================================
//    // ✏️ UPDATE USER
//    // =========================================================
//    public User updateUser(Long id, User updated) {
//
//        User user = userRepository.findById(id)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
//
//        user.setName(updated.getName());
//        user.setEmail(updated.getEmail());
//        user.setPhone(updated.getPhone());
//        user.setRole(updated.getRole());
//        user.setStatus(updated.getStatus());
//        user.setPreferences(updated.getPreferences());
//
//        User saved = userRepository.save(user);
//
//        invalidateUserCaches(id);
//
//        return saved;
//    }
//
//    // =========================================================
//    // ❌ DELETE USER
//    // =========================================================
//    public void deleteUser(Long id) {
//
//        userRepository.deleteById(id);
//
//        invalidateUserCaches(id);
//    }
//
//    // =========================================================
//    // 🔍 S1-F8 PROFILE (15 min DTO)
//    // =========================================================
//    @Cacheable(value = "S1-F8", key = "#userId")
//    public UserProfileDTO getUserProfile(Long userId) {
//
//        User user = getUserById(userId);
//
//        List<SavedAddressDTO> addressDTOs = user.getSavedAddresses()
//                .stream()
//                .map(addr -> new SavedAddressDTO(
//                        addr.getId(),
//                        addr.getLabel(),
//                        addr.getAddress(),
//                        addr.getLatitude(),
//                        addr.getLongitude(),
//                        addr.getIsDefault(),
//                        addr.getMetadata()
//                ))
//                .collect(Collectors.toList());
//
//        return new UserProfileDTO(
//                user.getId(),
//                user.getName(),
//                user.getEmail(),
//                user.getPhone(),
//                user.getPreferences(),
//                addressDTOs,
//                addressDTOs.size()
//        );
//    }
//
//    // =========================================================
//    // 🚫 DEACTIVATE USER
//    // =========================================================
//    @Transactional
//    public User deactivateUser(Long id) {
//
//        User user = userRepository.findById(id)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
//
//        Long activeRides = userRepository.countActiveRidesByUserId(id);
//
//        if (activeRides != null && activeRides > 0) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
//        }
//
//        user.setStatus(UserStatus.DEACTIVATED);
//
//        User saved = userRepository.save(user);
//
//        invalidateUserCaches(id);
//
//        return saved;
//    }
//
//    // =========================================================
//    // 🔍 S1-F5 FILTER USERS
//    // =========================================================
//    @Cacheable(value = "S1-F5", key = "T(java.util.Objects).hash(#key, #value)")
//    public List<User> filterUsersByPref(String key, Object value) {
//
//        return userRepository.findAll().stream()
//                .filter(u -> u.getPreferences() != null &&
//                        value.equals(u.getPreferences().get(key)))
//                .toList();
//    }
//
//    // =========================================================
//    // 🔍 S1-F5 (LANGUAGE FILTER)
//    // =========================================================
//    @Cacheable(value = "S1-F9", key = "T(java.util.Objects).hash(#lang, #minRides)")
//    public List<User> getUsersByLanguageAndMinRides(String lang, Integer minRides) {
//
//        if (minRides == null) minRides = 0;
//
//        return userRepository.findUsersByLanguageAndMinCompletedRides(lang, minRides);
//    }
//
//    // =========================================================
//    // 🔍 S1-F3 RIDES SUMMARY (DTO)
//    // =========================================================
//    @Cacheable(value = "S1-F3", key = "#userId")
//    public UserRideSummaryDTO getUserRideSummary(Long userId) {
//
//        User user = getUserById(userId);
//
//        List<Object[]> results = userRepository.getRideSummaryByUserId(userId);
//
//        if (results == null || results.isEmpty()) {
//            return new UserRideSummaryDTO(user.getId(), user.getName(),
//                    0, 0, 0, 0.0, 0.0);
//        }
//
//        Object[] r = results.get(0);
//
//        return new UserRideSummaryDTO(
//                ((Number) r[0]).longValue(),
//                (String) r[1],
//                ((Number) r[2]).intValue(),
//                ((Number) r[3]).intValue(),
//                ((Number) r[4]).intValue(),
//                ((Number) r[5]).doubleValue(),
//                ((Number) r[6]).doubleValue()
//        );
//    }
//
//    // =========================================================
//    // 🔍 S1-F6 TOP RIDERS (REPORT)
//    // =========================================================
//    @Cacheable(value = "S1-F6",
//            key = "T(java.util.Objects).hash(#startDate, #endDate, #limit)")
//    public List<TopRiderDTO> getTopRiders(LocalDateTime startDate,
//                                          LocalDateTime endDate,
//                                          int limit) {
//
//        if (startDate.isAfter(endDate)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
//        }
//
//        return userRepository.findTopRidersBySpending(startDate, endDate, limit)
//                .stream()
//                .map(r -> new TopRiderDTO(
//                        ((Number) r[0]).longValue(),
//                        (String) r[1],
//                        ((Number) r[2]).doubleValue(),
//                        ((Number) r[3]).longValue()
//                ))
//                .collect(Collectors.toList());
//    }
//
//    // =========================================================
//    // 🏠 SET DEFAULT ADDRESS
//    // =========================================================
//    @Transactional
//    public User setDefaultSavedAddress(Long userId, Long addressId) {
//
//        SavedAddress address = savedAddressRepository.findById(addressId)
//                .orElseThrow();
//
//        User user = userRepository.findById(userId)
//                .orElseThrow();
//
//        if (!address.getUser().getId().equals(userId)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
//        }
//
//        user.getSavedAddresses()
//                .forEach(a -> a.setDefault(a.getId().equals(addressId)));
//
//        savedAddressRepository.saveAll(user.getSavedAddresses());
//
//        invalidateUserCaches(userId);
//
//        return user;
//    }
//
//    // =========================================================
//    // 🔧 UPDATE USER PREFS
//    // =========================================================
//    public User updateUserPrefs(Long id, Map<String, Object> newPrefs) {
//
//        User user = userRepository.findById(id)
//                .orElseThrow();
//
//        user.getPreferences().putAll(newPrefs);
//
//        User saved = userRepository.save(user);
//
//        invalidateUserCaches(id);
//
//        return saved;
//    }
//
//    // =========================================================
//    // 🔥 CENTRAL INVALIDATION (SPEC-COMPLIANT)
//    // =========================================================
//    private void invalidateUserCaches(Long userId) {
//
//        // entity cache
//        cacheInvalidator.evictPattern("user-service::user::" + userId);
//
//        // feature caches (as per spec §4.4.6)
//        cacheInvalidator.evictPattern("user-service::S1-F1::*");
//        cacheInvalidator.evictPattern("user-service::S1-F3::*");
//        cacheInvalidator.evictPattern("user-service::S1-F5::*");
//        cacheInvalidator.evictPattern("user-service::S1-F6::*");
//        cacheInvalidator.evictPattern("user-service::S1-F8::*");
//        cacheInvalidator.evictPattern("user-service::S1-F9::*");
//
//    }
//}
package com.team21.uber.user.service;

import com.team21.uber.contracts.dto.RideSummaryDTO;
import com.team21.uber.contracts.feign.PaymentServiceClient;
import com.team21.uber.contracts.feign.RideServiceClient;
import com.team21.uber.user.auth.dto.UpdateRoleRequest;
import com.team21.uber.user.cache.CacheInvalidator;
import com.team21.uber.user.dto.*;
import com.team21.uber.user.events.EventPublisher;
import com.team21.uber.user.messaging.publishers.UserEventPublisher;
import com.team21.uber.user.model.*;
import com.team21.uber.user.repository.*;

import feign.FeignException;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SavedAddressRepository savedAddressRepository;
    private final CacheInvalidator cacheInvalidator;
    private final EventPublisher eventPublisher;
    private final UserEventPublisher userEventPublisher;
    private final RideServiceClient rideServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public UserService(UserRepository repo,
                       SavedAddressRepository savedAddressRepository,
                       CacheInvalidator cacheInvalidator,
                       EventPublisher eventPublisher,
                       UserEventPublisher userEventPublisher,
                       RideServiceClient rideServiceClient,
                       PaymentServiceClient paymentServiceClient) {
        this.userRepository = repo;
        this.savedAddressRepository = savedAddressRepository;
        this.cacheInvalidator = cacheInvalidator;
        this.eventPublisher = eventPublisher;
        this.userEventPublisher = userEventPublisher;
        this.rideServiceClient = rideServiceClient;
        this.paymentServiceClient=paymentServiceClient;
    }

    // ── S1-F1: Search Users ───────────────────────────────────────────────────

    @Cacheable(value = "S1-F1", key = "T(java.util.Objects).hash(#name, #email, #role)")
    public List<User> searchUsers(String name, String email, Role role) {
        if (name != null && name.isBlank()) name = null;
        if (email != null && email.isBlank()) email = null;

        if (name == null && email == null && role == null) {
            return userRepository.findAll();
        }

        String nameLike  = name  != null ? "%" + name.toLowerCase()  + "%" : null;
        String emailLike = email != null ? "%" + email.toLowerCase() + "%" : null;

        return userRepository.searchUsers(nameLike, emailLike, role);
    }

    // ── Create User ───────────────────────────────────────────────────────────

    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        if (userRepository.existsByPhone(user.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone already exists");
        }
        return userRepository.save(user);
    }

    // ── Get User By ID ────────────────────────────────────────────────────────

    @Cacheable(value = "user", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── Get All Users ─────────────────────────────────────────────────────────

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ── Update User ───────────────────────────────────────────────────────────

    public User updateUser(Long id, User updated) {
        User user = getUserById(id);

        user.setName(updated.getName());
        user.setEmail(updated.getEmail());
        user.setPhone(updated.getPhone());
        user.setStatus(updated.getStatus());
        user.setPreferences(updated.getPreferences());

        User saved = userRepository.save(user);
        invalidateUserCaches(id);
        return saved;
    }

    // ── Delete User ───────────────────────────────────────────────────────────

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        invalidateUserCaches(id);
    }

    // ── Deactivate User ───────────────────────────────────────────────────────

    @Transactional
    public User deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return user;
        }
        try {
            int activeRideCount = rideServiceClient.getUserActiveRideCount(id);
            if (activeRideCount > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active rides");
            }
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride service temporarily unavailable");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User saved = userRepository.save(user);
        invalidateUserCaches(id);
        userEventPublisher.publishUserDeactivated(id);
        return saved;
    }

    // ── S1-F8: User Profile ───────────────────────────────────────────────────

    @Cacheable(value = "S1-F8", key = "#userId")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<SavedAddressDTO> addressDTOs = user.getSavedAddresses()
                .stream()
                .map(addr -> SavedAddressDTO.builder()
                        .id(addr.getId())
                        .label(addr.getLabel())
                        .address(addr.getAddress())
                        .latitude(addr.getLatitude())
                        .longitude(addr.getLongitude())
                        .isDefault(addr.getIsDefault())
                        .metadata(addr.getMetadata())
                        .build()
                )
                .collect(Collectors.toList());

        return UserProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .savedAddresses(addressDTOs)
                .totalAddresses(addressDTOs.size())
                .build();
    }

    // ── S1-F5: Filter Users by Preference ────────────────────────────────────

    @Cacheable(value = "S1-F5", key = "T(java.util.Objects).hash(#key, #value)")
    public List<User> filterUsersByPref(String key, Object value) {
        if (key == null || value == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key or value is blank");
        }

        return userRepository.findAll()
                .stream()
                .filter(user -> {
                    Map<String, Object> prefs = user.getPreferences();
                    if (prefs == null || !prefs.containsKey(key)) return false;
                    return prefs.get(key).toString().equals(value.toString());
                })
                .toList();
    }

    // ── S1-F9: Filter by Language and Min Rides ───────────────────────────────

    @Cacheable(value = "S1-F9", key = "T(java.util.Objects).hash(#lang, #minRides)")
    public List<User> getUsersByLanguageAndMinRides(String lang, Integer minRides) {
        if (lang == null || lang.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lang must not be blank");
        }
        int threshold = (minRides == null ? 0 : minRides);
        //limit to 100 is applied on the query
        List<User> candidates = userRepository.findUsersByLanguagePreference(lang.trim());

        return candidates.stream()
                .filter(user -> {
                    try {
                        long count = rideServiceClient.getUserCompletedRideCount(user.getId());
                        return count >= threshold;
                    } catch (FeignException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
//        return userRepository.findUsersByLanguageAndMinCompletedRides(
//                lang.trim(),
//                minRides == null ? 0 : minRides
//        );
    }

    // ── S1-F3: Ride Summary ───────────────────────────────────────────────────

    @Cacheable(value = "S1-F3", key = "#userId")
    public UserRideSummaryDTO getUserRideSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            RideSummaryDTO summaryDTO = rideServiceClient.getUserRideSummary(userId);
            return UserRideSummaryDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .totalRides(summaryDTO.totalRides())
                    .completedRides(summaryDTO.completedRides())
                    .cancelledRides(summaryDTO.cancelledRides())
                    .totalSpent(summaryDTO.totalSpent())
                    .averageFare(summaryDTO.averageFare())
                    .build();

        }catch (FeignException.NotFound e) {
            return UserRideSummaryDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .totalRides(0)
                    .completedRides(0)
                    .cancelledRides(0)
                    .totalSpent(0.0)
                    .averageFare(0.0)
                    .build();
        }catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride service temporarily unavailable");
        }
    }

    // ── S1-F6: Top Riders ─────────────────────────────────────────────────────

    @Cacheable(value = "S1-F6", key = "T(java.util.Objects).hash(#startDate, #endDate, #limit)")
    public List<TopRiderDTO> getTopRiders(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        List<User> candidates = userRepository.findAll(PageRequest.of(0, 100)).getContent();

        String start = startDate.toLocalDate().toString(); // "2026-03-01"
        String end   = endDate.toLocalDate().toString();   // "2026-03-31"

        return candidates.stream()
                .map(user -> {
                    BigDecimal total;
                    try {
                        total = paymentServiceClient.getUserPaymentTotal(user.getId(), start, end);
                    } catch (FeignException.NotFound e) {
                        total = BigDecimal.ZERO;
                    } catch (FeignException e) {
                        total = BigDecimal.ZERO;
                    }
                    return TopRiderDTO.builder()
                            .userId(user.getId())
                            .name(user.getName())
                            .totalSpent(total.doubleValue())
                            .build();
                })
                .filter(dto -> dto.getTotalSpent() > 0)
                .sorted(Comparator.comparingDouble(TopRiderDTO::getTotalSpent).reversed())
                .limit(limit)
                .collect(Collectors.toList());

//        return userRepository.findTopRidersBySpending(startDate, endDate, limit)
//                .stream()
//                .map(row -> TopRiderDTO.builder()
//                        .userId(((Number) row[0]).longValue())
//                        .name((String) row[1])
//                        .totalSpent(((Number) row[2]).doubleValue())
//                        .rideCount(((Number) row[3]).longValue())
//                        .build()
//                )
//                .collect(Collectors.toList());
    }

    // ── S1-F7: Set Default Saved Address ──────────────────────────────────────

    @Transactional
    public User setDefaultSavedAddress(Long userId, Long addressId) {
        SavedAddress address = savedAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found for user");
        }

        user.getSavedAddresses()
                .forEach(a -> a.setDefault(a.getId().equals(addressId)));

        savedAddressRepository.saveAll(user.getSavedAddresses());
        invalidateUserCaches(userId);
        return user;
    }

    // ── S1-F2: Update User Preferences ───────────────────────────────────────

    public User updateUserPrefs(Long id, Map<String, Object> newPrefs) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.getPreferences().putAll(newPrefs);
        User saved = userRepository.save(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(saved.getId()));
        payload.put("action", "USER_UPDATED");
        payload.put("newPref", newPrefs);
        eventPublisher.notifyObservers("USER_UPDATED", payload);

        invalidateUserCaches(id);
        return saved;
    }

    // ── Update Role ───────────────────────────────────────────────────────────

    public User updateRole(Long id, UpdateRoleRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Role newRole;
        try {
            newRole = Role.valueOf(req.getRole());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + req.getRole());
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(saved.getId()));
        payload.put("action", "ROLE_CHANGED");
        payload.put("oldRole", oldRole != null ? oldRole.name() : null);
        payload.put("newRole", newRole.name());
        eventPublisher.notifyObservers("ROLE_CHANGED", payload);

        invalidateUserCaches(id);
        cacheInvalidator.evictPattern("*S1-F12::" + id + "*");

        return saved;
    }

    // ── Cache Invalidation ────────────────────────────────────────────────────

    private void invalidateUserCaches(Long userId) {
        cacheInvalidator.evictPattern("user-service::user::" + userId);
        cacheInvalidator.evictPattern("user-service::S1-F1::*");
        cacheInvalidator.evictPattern("user-service::S1-F3::*");
        cacheInvalidator.evictPattern("user-service::S1-F5::*");
        cacheInvalidator.evictPattern("user-service::S1-F6::*");
        cacheInvalidator.evictPattern("user-service::S1-F8::*");
        cacheInvalidator.evictPattern("user-service::S1-F9::*");
        cacheInvalidator.evictPattern("user-service::S1-F12::*");
    }
}