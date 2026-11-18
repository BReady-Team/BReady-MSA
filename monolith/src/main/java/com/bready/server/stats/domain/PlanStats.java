package com.bready.server.stats.domain;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.bready.server.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "plan_stats",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_plan_stats_plan_period",
                    columnNames = {"plan_id", "period"})
        })
public class PlanStats extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatsPeriod period;

    @Column(name = "total_triggers")
    private Integer totalTriggers;

    @Column(name = "total_switches")
    private Integer totalSwitches;

    @Column(name = "reliability_score", precision = 5, scale = 2)
    private BigDecimal reliabilityScore;

    public void update(int totalTriggers, int totalSwitches, BigDecimal reliabilityScore) {
        this.totalTriggers = totalTriggers;
        this.totalSwitches = totalSwitches;
        this.reliabilityScore = reliabilityScore;
    }
}
