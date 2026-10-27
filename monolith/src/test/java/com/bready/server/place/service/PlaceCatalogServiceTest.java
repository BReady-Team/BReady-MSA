package com.bready.server.place.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.domain.Place;
import com.bready.server.place.exception.PlaceErrorCase;
import com.bready.server.place.fixture.PlaceFixture;
import com.bready.server.place.repository.PlaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceCatalogServiceTest {

    private static final Long PLACE_ID = 5L;

    @Mock
    private PlaceRepository placeRepository;

    @InjectMocks
    private PlaceCatalogService placeCatalogService;

    @Test
    @DisplayName("성공 - 외부 ID가 같은 장소가 이미 있으면 저장하지 않고 그 장소를 돌려준다")
    void register_existingPlace() {
        given(placeRepository.findByExternalId(PlaceFixture.EXTERNAL_ID))
                .willReturn(Optional.of(PlaceFixture.savedPlace(PLACE_ID)));

        PlaceInfo place = placeCatalogService.register(PlaceFixture.registerRequest());

        assertThat(place.id()).isEqualTo(PLACE_ID);
        then(placeRepository).should().findByExternalId(PlaceFixture.EXTERNAL_ID);
        then(placeRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("성공 - 처음 보는 장소면 저장하고 엔티티가 아닌 PlaceInfo로 돌려준다")
    void register_newPlace() {
        given(placeRepository.findByExternalId(PlaceFixture.EXTERNAL_ID)).willReturn(Optional.empty());
        given(placeRepository.saveAndFlush(any(Place.class))).willReturn(PlaceFixture.savedPlace(PLACE_ID));

        PlaceInfo place = placeCatalogService.register(PlaceFixture.registerRequest());

        assertThat(place)
                .isEqualTo(new PlaceInfo(
                        PLACE_ID,
                        PlaceFixture.EXTERNAL_ID,
                        PlaceFixture.NAME,
                        PlaceFixture.ADDRESS,
                        PlaceFixture.LATITUDE,
                        PlaceFixture.LONGITUDE,
                        true));
    }

    @Test
    @DisplayName("성공 - 저장하는 사이 다른 요청이 먼저 저장했으면 다시 조회해서 그 장소를 돌려준다")
    void register_concurrentInsert() {
        given(placeRepository.findByExternalId(PlaceFixture.EXTERNAL_ID))
                .willReturn(Optional.empty(), Optional.of(PlaceFixture.savedPlace(PLACE_ID)));
        given(placeRepository.saveAndFlush(any(Place.class))).willThrow(DataIntegrityViolationException.class);

        PlaceInfo place = placeCatalogService.register(PlaceFixture.registerRequest());

        assertThat(place.id()).isEqualTo(PLACE_ID);
    }

    @Test
    @DisplayName("실패 - 저장도 재조회도 안 되면 PLACE_SAVE_FAILED")
    void register_saveFailed() {
        given(placeRepository.findByExternalId(PlaceFixture.EXTERNAL_ID)).willReturn(Optional.empty());
        given(placeRepository.saveAndFlush(any(Place.class))).willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> placeCatalogService.register(PlaceFixture.registerRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.PLACE_SAVE_FAILED);
    }
}
