package com.bready.server.stats.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bready.server.plan.api.PlanQueryApi;
import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.dto.StatsSummaryResponse;
import com.bready.server.trigger.api.ActivityQueryApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private PlanQueryApi planQueryApi;

    @Mock
    private ActivityQueryApi activityQueryApi;

    @InjectMocks
    private StatsService statsService;

    @Test
    @DisplayName("성공 - Planning에서 받은 플랜 수·전환 수·결정 수로 요약을 만들고 평균은 소수 첫째 자리로 반올림한다")
    void getSummary_success() {
        // given
        given(planQueryApi.countActivePlans(OWNER_ID)).willReturn(3L);
        given(activityQueryApi.countSwitches(OWNER_ID, null)).willReturn(4L);
        given(activityQueryApi.countDecisions(OWNER_ID, null)).willReturn(7L);

        // when
        StatsSummaryResponse response = statsService.getSummary(OWNER_ID, StatsPeriod.ALL);

        // then
        assertThat(response.totalPlans()).isEqualTo(3L);
        assertThat(response.totalSwitches()).isEqualTo(4L);
        assertThat(response.recentCount()).isEqualTo(7L);
        assertThat(response.avgSwitchesPerPlan()).isEqualTo(1.3);
    }

    @Test
    @DisplayName("성공 - 플랜이 없으면 평균 전환 수는 0.0")
    void getSummary_noPlans() {
        // given
        given(planQueryApi.countActivePlans(OWNER_ID)).willReturn(0L);
        given(activityQueryApi.countSwitches(OWNER_ID, null)).willReturn(0L);
        given(activityQueryApi.countDecisions(OWNER_ID, null)).willReturn(0L);

        // when
        StatsSummaryResponse response = statsService.getSummary(OWNER_ID, StatsPeriod.ALL);

        // then
        assertThat(response.avgSwitchesPerPlan()).isEqualTo(0.0);
    }
}
