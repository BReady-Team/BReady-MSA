package com.bready.catalog.place.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bready.catalog.global.response.CommonResponse;
import com.bready.catalog.place.dto.PlaceRegisterRequest;
import com.bready.catalog.place.dto.PlaceRegisterResponse;
import com.bready.catalog.place.service.PlaceRegisterService;

import lombok.RequiredArgsConstructor;

// 모놀리식(후보 추가)만 부르는 내부 API
@RestController
@RequestMapping("/internal/v1/places")
@RequiredArgsConstructor
public class PlaceRegisterInternalController {

    private final PlaceRegisterService placeRegisterService;

    @PostMapping
    public CommonResponse<PlaceRegisterResponse> registerPlace(@Valid @RequestBody PlaceRegisterRequest request) {
        return CommonResponse.success(placeRegisterService.register(request));
    }
}
