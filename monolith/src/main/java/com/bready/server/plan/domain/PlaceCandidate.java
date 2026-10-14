package com.bready.server.plan.domain;

import jakarta.persistence.*;

import org.hibernate.annotations.SQLRestriction;

import com.bready.server.global.entity.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "place_candidates",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_category_place",
                    columnNames = {"category_id", "place_id"})
        })
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCandidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 카테고리의 후보인가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private PlanCategory category; // PlanCategory와의 연관관계 설정 (1:N)

    // 장소는 Place Catalog 소유라 연관관계 대신 ID로 참조
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    public static PlaceCandidate create(PlanCategory category, Long placeId) {
        if (category == null) {
            throw new IllegalArgumentException("category는 필수입니다.");
        }
        if (placeId == null) {
            throw new IllegalArgumentException("placeId는 필수입니다.");
        }
        PlaceCandidate candidate = new PlaceCandidate();
        candidate.category = category;
        candidate.placeId = placeId;
        return candidate;
    }
}
