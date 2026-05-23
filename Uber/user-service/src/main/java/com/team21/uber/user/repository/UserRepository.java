package com.team21.uber.user.repository;

import com.team21.uber.user.model.Role;
import com.team21.uber.user.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);


    @Query(value = """
        SELECT u.*
        FROM users u
        JOIN rides r ON r.user_id = u.id
        WHERE u.preferences ->> 'language' = :lang
          AND r.status = 'COMPLETED'
        GROUP BY u.id
        HAVING COUNT(r.id) >= :minRides
        """, nativeQuery = true)
    List<User> findUsersByLanguageAndMinCompletedRides(
            @Param("lang") String lang,
            @Param("minRides") Integer minRides
    );

    @Query("""
        SELECT u FROM User u
        WHERE
            (:name IS NULL OR LOWER(u.name) LIKE :name)
            AND
            (:email IS NULL OR LOWER(u.email) LIKE :email)
            AND
            (:role IS NULL OR u.role = :role)
    """)
    List<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("role") Role role
    );

    @Query(value = """
        SELECT 
            u.id,
            u.name,
            COUNT(r.id) AS totalRides,
            SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedRides,
            SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledRides,
            COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN r.fare ELSE 0 END), 0) AS totalSpent,
            COALESCE(AVG(CASE WHEN r.status = 'COMPLETED' THEN r.fare END), 0) AS averageFare
        FROM users u
        LEFT JOIN rides r ON r.user_id = u.id
        WHERE u.id = :userId
        GROUP BY u.id, u.name
    """, nativeQuery = true)
    List<Object[]> getRideSummaryByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT u.id, u.name, 
               COALESCE(SUM(r.fare), 0) AS total_spent,
               COUNT(r.id) AS ride_count
        FROM users u
        LEFT JOIN rides r 
            ON r.user_id = u.id
            AND r.status = 'COMPLETED'
            AND r.requested_at BETWEEN :startDate AND :endDate
        GROUP BY u.id, u.name
        ORDER BY total_spent DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findTopRidersBySpending(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM rides
        WHERE user_id = :userId
          AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')
        """, nativeQuery = true)
    Long countActiveRidesByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM users WHERE preferences->>'language' = :lang LIMIT 100", nativeQuery = true)
    List<User> findUsersByLanguagePreference(@Param("lang") String lang);
}