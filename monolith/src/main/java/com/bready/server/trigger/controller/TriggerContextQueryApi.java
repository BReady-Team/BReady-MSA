package com.bready.server.trigger.controller;

import jakarta.validation.constraints.Positive;

import com.bready.server.global.response.CommonResponse;
import com.bready.server.trigger.api.RepresentativePlace;
import com.bready.server.trigger.api.TriggerContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "TriggerContext (내부)", description = "다른 서비스가 트리거가 난 상황을 조회합니다.")
public interface TriggerContextQueryApi {

    @Operation(summary = "트리거 상황 조회", description = "트리거 종류, 플랜 지역, 트리거가 난 카테고리와 플랜의 전체 카테고리를 돌려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "남의 플랜의 트리거 (4002)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "트리거 없음 (7001)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    CommonResponse<TriggerContext> getTriggerContext(@Parameter(hidden = true) Long userId, @Positive Long triggerId);

    @Operation(summary = "트리거가 난 카테고리의 대표 장소 조회", description = "대표 후보에 복사해 둔 장소 스냅샷을 돌려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(
                responseCode = "400",
                description = "대표 후보가 아직 없음 (7005)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "남의 플랜의 트리거 (4002)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "트리거 없음 (7001), 대표 후보가 삭제됨 (4110)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    CommonResponse<RepresentativePlace> getRepresentativePlace(
            @Parameter(hidden = true) Long userId, @Positive Long triggerId);
}
