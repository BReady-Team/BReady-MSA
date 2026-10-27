package com.bready.server.place.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.api.PlaceRegisterRequest;
import com.bready.server.place.domain.Place;
import com.bready.server.place.exception.PlaceErrorCase;
import com.bready.server.place.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceCatalogService implements PlaceCatalogApi {

    private final PlaceRepository placeRepository;

    // 장소는 공용 데이터라 부른 쪽의 트랜잭션이 롤백되어도 남긴다
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlaceInfo register(PlaceRegisterRequest request) {
        try {
            Place place = placeRepository
                    .findByExternalId(request.externalId())
                    .orElseGet(() -> placeRepository.saveAndFlush(Place.create(
                            request.externalId(),
                            request.name(),
                            request.address(),
                            request.latitude(),
                            request.longitude(),
                            request.isIndoor())));
            return toInfo(place);
        } catch (DataIntegrityViolationException e) {
            // 다른 트랜잭션이 먼저 insert 한 경우는 재조회로 해결
            return placeRepository
                    .findByExternalId(request.externalId())
                    .map(this::toInfo)
                    .orElseThrow(() -> ApplicationException.from(PlaceErrorCase.PLACE_SAVE_FAILED));
        }
    }

    private PlaceInfo toInfo(Place place) {
        return new PlaceInfo(
                place.getId(),
                place.getExternalId(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getIsIndoor());
    }
}
