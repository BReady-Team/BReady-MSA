package com.bready.server.plan.domain;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "category_states",
        uniqueConstraints = {@UniqueConstraint(columnNames = "category_id")})
public class CategoryState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "current_candidate_id")
    private Long currentCandidateId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, unique = true)
    private PlanCategory category; // PlanCategory와의 연관관계 설정 (1:1)

    public static CategoryState createEmpty(PlanCategory category, LocalDateTime createdAt) {
        CategoryState state = new CategoryState();
        state.category = category;
        state.updatedAt = createdAt;
        return state;
    }

    public boolean hasRepresentative() {
        return currentCandidateId != null;
    }

    public void changeRepresentative(Long representativeCandidateId) {
        this.currentCandidateId = representativeCandidateId;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isRepresentative(Long candidateId) {
        return this.currentCandidateId != null && this.currentCandidateId.equals(candidateId);
    }
}
