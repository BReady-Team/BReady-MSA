package com.bready.server.plan.fixture;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;

public final class PlaceCandidateFixture {

    public static final Long PLAN_ID = 1L;
    public static final Long CATEGORY_ID = 10L;
    public static final Long PLACE_ID = 5L;
    public static final String EXTERNAL_ID = "kakao-123";
    public static final String NAME = "성수 카페";
    public static final String ADDRESS = "서울 성동구";
    public static final BigDecimal LATITUDE = new BigDecimal("37.544500");
    public static final BigDecimal LONGITUDE = new BigDecimal("127.055700");

    private PlaceCandidateFixture() {}

    public static PlanCategory cafeCategoryOwnedBy(Long ownerId) {
        Plan plan = Plan.create(ownerId, "제목", LocalDate.of(2026, 10, 20), "서울");
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        PlanCategory category = PlanCategory.create(plan, PlaceCategoryType.CAFE, 1);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
        return category;
    }

    public static PlaceCandidate saved(PlaceCandidate candidate, Long id) {
        ReflectionTestUtils.setField(candidate, "id", id);
        return candidate;
    }

    public static PlaceCandidateCreateRequest createRequest() {
        return new PlaceCandidateCreateRequest(
                PLAN_ID, CATEGORY_ID, EXTERNAL_ID, NAME, ADDRESS, LATITUDE, LONGITUDE, true);
    }

    public static PlaceInfo registeredPlace() {
        return new PlaceInfo(PLACE_ID, EXTERNAL_ID, NAME, ADDRESS, LATITUDE, LONGITUDE, true);
    }
}
