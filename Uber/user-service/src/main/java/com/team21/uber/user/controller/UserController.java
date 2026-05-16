package com.team21.uber.user.controller;

import com.team21.uber.user.auth.dto.UpdateRoleRequest;
import com.team21.uber.user.model.Role;
import com.team21.uber.user.model.User;
import com.team21.uber.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.team21.uber.user.dto.UserProfileDTO;
import com.team21.uber.user.model.User;
import org.springframework.http.ResponseEntity;

import com.team21.uber.user.dto.UserRideSummaryDTO;
import com.team21.uber.user.model.Role;
import com.team21.uber.user.model.User;
import com.team21.uber.user.dto.TopRiderDTO;
import com.team21.uber.user.model.Role;
import com.team21.uber.user.model.User;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService service){
        userService = service;
    }
    @GetMapping("/search")
    public List<User> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role
    ) {
        return userService.searchUsers(name, email, role);
    }

    // ── CRUD ────────────────────────────────────────────────────────────────


    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    //S1-F8

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getUserProfile(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    @PutMapping("/{id}/deactivate")
    public User deactivateUser(@PathVariable Long id,
                               @RequestHeader("X-User-Id") Long callerId,
                               @RequestHeader("X-User-Role") String callerRole) {

        // must be the same user OR an ADMIN
        if (!callerId.equals(id) && !callerRole.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not authorized to access this resource");
        }
        return userService.deactivateUser(id);
    }

    @GetMapping("/preferences/language")
    public ResponseEntity<List<User>> getUsersByLanguage(
            @RequestParam String lang,
            @RequestParam Integer minRides
    ) {
        return ResponseEntity.ok(userService.getUsersByLanguageAndMinRides(lang, minRides)
        );
    }
    @GetMapping("/reports/top-riders")
    public ResponseEntity<List<TopRiderDTO>> getTopRiders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int limit) {
        try {
            return ResponseEntity.ok(userService.getTopRiders(
                    startDate.atStartOfDay(),
                    endDate.atTime(23, 59, 59),
                    limit));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    //    @GetMapping("/{id}/ride-summary")
//    public ResponseEntity<UserRideSummaryDTO> getUserRideSummary(@PathVariable Long id) {
//        try {
//            return ResponseEntity.ok(userService.getUserRideSummary(id));
//        } catch (RuntimeException e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
    @GetMapping("/{id}/ride-summary")
    public ResponseEntity<UserRideSummaryDTO> getUserRideSummary(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long callerId,
        @RequestHeader("X-User-Role") String callerRole) {

            if (!callerId.equals(id) && !callerRole.equals("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not authorized to access this resource");
            }

            return ResponseEntity.ok(userService.getUserRideSummary(id));
    }
    @GetMapping("/{id}")
    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getUserById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    @PreAuthorize("#id == authentication.principal or hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable Long id,
                                           @RequestBody User user) {
        try {
            return ResponseEntity.ok(userService.updateUser(id, user));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping(value = "/preferences/search", params = {"key", "value"})
    public List<User> filterUsersByPref(@RequestParam String key, @RequestParam String value){
        return userService.filterUsersByPref(key,value);
    }

    @PutMapping("/{userId}/addresses/{addressId}/default")
    public User setDefaultSavedAddress(@PathVariable Long userId, @PathVariable Long addressId){
        return userService.setDefaultSavedAddress(userId,addressId);
    }

    @PutMapping("/{id}/preferences")
    public User updateUserPrefs(
            @PathVariable Long id,
            @RequestBody Map<String, Object> newPrefs){

        return userService.updateUserPrefs(id, newPrefs);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateRole(@PathVariable Long id,
                                           @RequestBody UpdateRoleRequest req){
        System.out.println("🔥 ROLE ENDPOINT HIT");
        User saved = userService.updateRole(id, req);
        return ResponseEntity.ok(saved);
    }
    @PutMapping("/{id}/role-test")
    public String testRole() {
        System.out.println("🔥 ROLE Test ENDPOINT HIT");

        return "ROLE WORKS";
    }
}

