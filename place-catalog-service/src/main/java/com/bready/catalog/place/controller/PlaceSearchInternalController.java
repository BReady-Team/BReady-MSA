package com.bready.catalog.place.controller;

import java.util.List;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bready.catalog.global.response.CommonResponse;
import com.bready.catalog.place.dto.PlaceSearchRequest;
import com.bready.catalog.place.dto.PlaceSearchResponse;
import com.bready.catalog.place.service.PlaceSearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/places")
@RequiredArgsConstructor
public class PlaceSearchInternalController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/search")
    public CommonResponse<List<PlaceSearchResponse>> searchPlaces(@Valid @ModelAttribute PlaceSearchRequest request) {
        return CommonResponse.success(placeSearchService.search(request));
    }
}
