package com.team21.uber.user.controller;

import com.team21.uber.user.adapter.MongoDocumentAdapter;
import com.team21.uber.user.dto.UserActivityFeedItemDTO;
import com.team21.uber.user.events.AuthEvent;
import com.team21.uber.user.events.AuthEventRepository;
import com.team21.uber.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * S1-F12 — Get User Activity Feed
 * Endpoint: GET /api/users/{id}/activity?page={page}&size={size}
 * Auth: Required (USER)
 * Databases: MongoDB (auth_events), PostgreSQL (user existence), Redis (5-min cache)
 */
@RestController
@RequestMapping("/api/users")
public class UserActivityController {

    private final ObjectProvider<AuthEventRepository> repoProvider;
    private final MongoDocumentAdapter adapter;
    private final UserRepository userRepository;

    public UserActivityController(ObjectProvider<AuthEventRepository> repoProvider,
                                  MongoDocumentAdapter adapter,
                                  UserRepository userRepository) {
        this.repoProvider = repoProvider;
        this.adapter = adapter;
        this.userRepository = userRepository;
    }
    @GetMapping("/{id}/activity")
    @Cacheable(value = "search-5m", key = "'S1-F12::' + #id + '::' + #page + '::' + #size")
    public ResponseEntity<Map<String, Object>> getActivityFeed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // [S1-F12-a] JWT validation handled upstream by JwtAuthenticationFilter
        // [S1-F12-b] Ownership check: caller uid must equal path {id}, or caller is ADMIN
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            System.out.println("Requested user id = " + id);
            Object principal = auth.getPrincipal();
            // JwtAuthenticationFilter sets principal to userId (Long)
            if (principal instanceof Long callerId) {
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (!isAdmin && !callerId.equals(id)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied: you can only view your own activity feed");
                }
            }
        }

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
        if (size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be >= 1");
        }

        // [S1-F12-c] Verify user exists in PostgreSQL — throws 404 if not found
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        System.out.println("User Exists in Repo");
        // [S1-F12-d] Query MongoDB auth_events sorted by most recent first, paginated
        // Cap size at 100 per spec; default page=0, size=10
        int cappedSize = Math.min(size, 100);

        AuthEventRepository repo = repoProvider.getIfAvailable();
        System.out.println("We got the Repo"+repo);

        if (repo == null) {
            System.out.println("Repo Returned empty");
            return ResponseEntity.ok(buildResponse(Collections.emptyList(), page, cappedSize, 0));
        }

        try {
            System.out.println("Entered try with id"+id);

            List<AuthEvent> all = repo.findByUserIdOrderByTimestampDesc((Long)id);
            System.out.println("Repo is not empty");

            int total = all.size();
            System.out.println("Total elements are"+total);

            int from = Math.min(page * cappedSize, total);
            int to = Math.min(from + cappedSize, total);
            List<UserActivityFeedItemDTO> content = all.subList(from, to)
                    .stream()
                    .map(adapter::adapt)
                    .toList();

            // [S1-F12-f] Return paginated envelope with status 200
            return ResponseEntity.ok(buildResponse(content, page, cappedSize, total));
//        } catch (Exception ex) {
//            return ResponseEntity.ok(buildResponse(Collections.emptyList(), page, cappedSize, 0));
//        }
        } catch (Exception ex) {
            System.err.println("ERROR: Failed to fetch activity feed for userId="
                    + id + ", page=" + page + ", size=" + cappedSize);
            ex.printStackTrace();

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to retrieve user activity feed at this time",
                    ex
            );
        }
    }

    // Builds the spec-required response shape: {content, page, size, totalElements}
    private Map<String, Object> buildResponse(List<?> content, int page, int size, int total) {
        return Map.of(
                "content", content,
                "page", page,
                "size", size,
                "totalElements", total
        );
    }
}