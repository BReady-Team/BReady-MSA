package com.bready.server.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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

    // 플랜 상세·추천이 Catalog를 부르지 않고 읽는다
    @Embedded
    private PlaceSnapshot placeSnapshot;

    public static PlaceCandidate create(PlanCategory category, Long placeId, PlaceSnapshot placeSnapshot) {
        PlaceCandidate candidate = new PlaceCandidate();
        candidate.category = category;
        candidate.placeId = placeId;
        candidate.placeSnapshot = placeSnapshot;
        return candidate;
    }
}
