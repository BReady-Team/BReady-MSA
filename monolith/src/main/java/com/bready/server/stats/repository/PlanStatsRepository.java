package com.bready.server.stats.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bready.server.stats.domain.PlanStats;
import com.bready.server.stats.domain.StatsPeriod;

public interface PlanStatsRepository extends JpaRepository<PlanStats, Long> {
    List<PlanStats> findByPeriodAndPlanIdIn(StatsPeriod period, List<Long> planIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        select ps
        from PlanStats ps
        where ps.planId = :planId
          and ps.period = :period
    """)
    Optional<PlanStats> findByPlanIdAndPeriodForUpdate(
            @Param("planId") Long planId, @Param("period") StatsPeriod period);

    @Modifying
    @Query(
            value =
                    """
        insert into plan_stats (plan_id, period, total_triggers, total_switches, reliability_score, created_at, updated_at)
        values (:planId, :period, 0, 0, 0, :now, :now)
        on duplicate key update plan_id = plan_id
    """,
            nativeQuery = true)
    void insertIfAbsent(@Param("planId") Long planId, @Param("period") String period, @Param("now") LocalDateTime now);
}
