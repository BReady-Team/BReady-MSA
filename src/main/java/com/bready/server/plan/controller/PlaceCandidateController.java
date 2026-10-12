package com.bready.server.plan.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bready.server.global.auth.CurrentUser;
import com.bready.server.global.response.CommonResponse;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.dto.PlaceCandidateDeleteResponse;
import com.bready.server.plan.dto.PlaceCandidateRepresentativeResponse;
import com.bready.server.plan.service.PlaceCandidateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/places")
@Validated
@RequiredArgsConstructor
public class PlaceCandidateController {

    private final PlaceCandidateService placeCandidateService;

    @PostMapping("/candidates")
    @Operation(summary = "장소 후보 등록", description = "장소 검색 결과로 선택한 장소를 특정 PlanCategory의 장소 후보로 등록합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "403",
                description = "플랜 소유자가 아님 (4002)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "장소 후보 등록 성공",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (필수 값 누락, 유효하지 않은 요청 값)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 해당 카테고리에 등록된 장소 후보",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public CommonResponse<PlaceCandidateCreateResponse> createCandidate(
            @CurrentUser Long userId, @RequestBody @Valid PlaceCandidateCreateRequest request) {
        return CommonResponse.success(placeCandidateService.createCandidate(userId, request));
    }

    @PostMapping("/candidates/{candidateId}/representative")
    @Operation(summary = "대표 장소 후보 선택", description = "특정 카테고리에서 대표로 사용할 장소 후보를 선택합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "403",
                description = "플랜 소유자가 아님 (4002)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "대표 장소 후보 선택 성공",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 장소 후보",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 대표 장소로 선택된 후보",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public CommonResponse<PlaceCandidateRepresentativeResponse> setRepresentative(
            @CurrentUser Long userId,
            @Parameter(description = "대표로 선택할 장소 후보 ID", example = "12", required = true) @Positive @PathVariable
                    Long candidateId) {
        return CommonResponse.success(placeCandidateService.setRepresentative(userId, candidateId));
    }

    @DeleteMapping("/candidates/{candidateId}")
    @Operation(summary = "장소 후보 삭제", description = "특정 카테고리에 등록된 장소 후보를 삭제합니다. (대표 후보로 선택된 장소는 삭제 불가)")
    @ApiResponses({
        @ApiResponse(
                responseCode = "403",
                description = "플랜 소유자가 아님 (4002)",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "200",
                description = "삭제 성공",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 후보",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "대표 장소는 삭제 불가",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public CommonResponse<PlaceCandidateDeleteResponse> deleteCandidate(
            @CurrentUser Long userId,
            @Parameter(description = "삭제할 장소 후보 ID", example = "12", required = true) @Positive @PathVariable
                    Long candidateId) {
        return CommonResponse.success(placeCandidateService.deleteCandidate(userId, candidateId));
    }
}
