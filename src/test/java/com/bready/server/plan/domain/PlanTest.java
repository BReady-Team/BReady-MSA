package com.bready.server.plan.domain;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.plan.exception.PlanErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("플랜 - 소유권")
class PlanTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Test
    @DisplayName("성공 - 소유자면 검증을 통과")
    void validateOwner_owner() {
        Plan plan = Plan.create(OWNER_ID, "성수 나들이", LocalDate.of(2026, 10, 20), "서울");

        assertThat(plan.isOwnedBy(OWNER_ID)).isTrue();
        assertThatCode(() -> plan.validateOwner(OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실패 - 소유자가 아니면 PLAN_ACCESS_DENIED")
    void validateOwner_notOwner() {
        Plan plan = Plan.create(OWNER_ID, "성수 나들이", LocalDate.of(2026, 10, 20), "서울");

        assertThat(plan.isOwnedBy(OTHER_USER_ID)).isFalse();
        assertThatThrownBy(() -> plan.validateOwner(OTHER_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlanErrorCase.PLAN_ACCESS_DENIED);
    }
}
