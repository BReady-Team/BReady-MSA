package com.bready.recommendation.planning.external;

import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.recommendation.global.client.RemoteErrorTranslator;
import com.bready.recommendation.global.client.RemoteResponse;
import com.bready.recommendation.global.exception.ApplicationException;
import com.bready.recommendation.global.exception.RelayedErrorCase;
import com.bready.recommendation.planning.api.RepresentativePlace;
import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerContextApi;
import com.bready.recommendation.planning.exception.PlanningErrorCase;

import lombok.RequiredArgsConstructor;

// TriggerContextApi의 구현이 모놀리식에서는 메서드 호출이지만 여기서는 HTTP 호출로 변경
@Component
@RequiredArgsConstructor
public class TriggerContextHttpAdapter implements TriggerContextApi {

    private static final String TARGET = "Planning";

    private static final RelayedErrorCase UNAUTHORIZED =
            new RelayedErrorCase(HttpStatus.UNAUTHORIZED.value(), 401, "로그인이 필요합니다.");

    private final PlanningHttpClient planningHttpClient;

    @Override
    public TriggerContext getTriggerContext(String authorization, Long triggerId) {
        return call(() -> planningHttpClient.getTriggerContext(authorization, triggerId));
    }

    @Override
    public RepresentativePlace getRepresentativePlace(String authorization, Long triggerId) {
        return call(() -> planningHttpClient.getRepresentativePlace(authorization, triggerId));
    }

    private <T> T call(Supplier<RemoteResponse<T>> request) {
        try {
            return request.get().data();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw ApplicationException.from(UNAUTHORIZED, e);
            }
            throw RemoteErrorTranslator.fromErrorResponse(TARGET, e, PlanningErrorCase.PLANNING_UNAVAILABLE);
        } catch (ResourceAccessException e) {
            throw RemoteErrorTranslator.fromConnectionFailure(TARGET, e, PlanningErrorCase.PLANNING_UNAVAILABLE);
        }
    }
}
