package com.team21.uber.user.adapter;

import com.team21.uber.user.dto.UserRideSummaryDTO;
import org.springframework.stereotype.Component;

/**
 * Adapter that maps a native-SQL {@code Object[]} projection row into a
 * {@link UserRideSummaryDTO}. Wraps native Object[] projections returned by
 * Object[] native projection (see {@code UserService.getUserRideSummary}).
 *
 * Column order MUST match the existing native query exactly:
 *   [0] userId           (Number -> Long)
 *   [1] name             (String)
 *   [2] totalRides       (Number -> Integer)
 *   [3] completedRides   (Number -> Integer)
 *   [4] cancelledRides   (Number -> Integer)
 *   [5] totalSpent       (Number -> Double)
 *   [6] averageFare      (Number -> Double)
 */
@Component
public class ObjectArrayDtoAdapter {

    public UserRideSummaryDTO adapt(Object[] row) {
        return UserRideSummaryDTO.builder()
                .userId(((Number) row[0]).longValue())
                .name((String) row[1])
                .totalRides(((Number) row[2]).intValue())
                .completedRides(((Number) row[3]).intValue())
                .cancelledRides(((Number) row[4]).intValue())
                .totalSpent(((Number) row[5]).doubleValue())
                .averageFare(((Number) row[6]).doubleValue())
                .build();
    }
}
