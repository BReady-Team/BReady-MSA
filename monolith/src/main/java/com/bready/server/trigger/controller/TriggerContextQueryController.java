package com.bready.server.trigger.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bready.server.global.auth.CurrentUser;
import com.bready.server.global.response.CommonResponse;
import com.bready.server.trigger.api.RepresentativePlace;
import com.bready.server.trigger.api.TriggerContext;
import com.bready.server.trigger.api.TriggerContextApi;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/triggers")
@RequiredArgsConstructor
@Validated
public class TriggerContextQueryController implements TriggerContextQueryApi {

    private final TriggerContextApi triggerContextApi;

    @Override
    @GetMapping("/{triggerId}/context")
    public CommonResponse<TriggerContext> getTriggerContext(@CurrentUser Long userId, @PathVariable Long triggerId) {
        return CommonResponse.success(triggerContextApi.getTriggerContext(userId, triggerId));
    }

    @Override
    @GetMapping("/{triggerId}/representative-place")
    public CommonResponse<RepresentativePlace> getRepresentativePlace(
            @CurrentUser Long userId, @PathVariable Long triggerId) {
        return CommonResponse.success(triggerContextApi.getRepresentativePlace(userId, triggerId));
    }
}
