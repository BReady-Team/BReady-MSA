package com.bready.catalog.place.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bready.catalog.place.domain.Place;
import com.bready.catalog.place.dto.PlaceRegisterRequest;
import com.bready.catalog.place.dto.PlaceRegisterResponse;
import com.bready.catalog.place.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceRegisterService {

    private final PlaceRepository placeRepository;

    // 외부 ID가 같은 장소가 있으면 그 장소를, 없으면 새로 저장해서 돌려준다
    public PlaceRegisterResponse register(PlaceRegisterRequest request) {
        Place place = placeRepository.findByExternalId(request.externalId()).orElseGet(() -> save(request));
        return PlaceRegisterResponse.from(place);
    }

    private Place save(PlaceRegisterRequest request) {
        try {
            return placeRepository.saveAndFlush(Place.create(
                    request.externalId(),
                    request.name(),
                    request.address(),
                    request.latitude(),
                    request.longitude(),
                    request.isIndoor()));
        } catch (DataIntegrityViolationException e) {
            return placeRepository.findByExternalId(request.externalId()).orElseThrow(() -> e);
        }
    }
}
