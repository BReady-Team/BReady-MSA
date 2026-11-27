package com.bready.recommendation.planning.external;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.bready.recommendation.global.client.RemoteResponse;
import com.bready.recommendation.planning.api.RepresentativePlace;
import com.bready.recommendation.planning.api.TriggerContext;

// 모놀리식의 Planning 내부 API
@HttpExchange("/internal/v1/triggers")
public interface PlanningHttpClient {

    @GetExchange("/{triggerId}/context")
    RemoteResponse<TriggerContext> getTriggerContext(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long triggerId);

    @GetExchange("/{triggerId}/representative-place")
    RemoteResponse<RepresentativePlace> getRepresentativePlace(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long triggerId);
}
