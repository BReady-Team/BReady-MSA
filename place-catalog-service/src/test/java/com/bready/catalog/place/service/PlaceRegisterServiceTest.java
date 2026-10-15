package com.bready.catalog.place.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bready.catalog.place.domain.Place;
import com.bready.catalog.place.dto.PlaceRegisterRequest;
import com.bready.catalog.place.dto.PlaceRegisterResponse;
import com.bready.catalog.place.repository.PlaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceRegisterServiceTest {

    @Mock
    PlaceRepository placeRepository;

    @InjectMocks
    PlaceRegisterService placeRegisterService;

    @Test
    @DisplayName("성공 - 외부 ID가 같은 장소가 있으면 저장하지 않고 그 장소를 돌려준다")
    void register_existingPlace() {
        Place existing = place("이미 있는 카페");
        given(placeRepository.findByExternalId("kakao-1")).willReturn(Optional.of(existing));

        PlaceRegisterResponse response = placeRegisterService.register(request());

        assertThat(response.name()).isEqualTo("이미 있는 카페");
        then(placeRepository).should().findByExternalId("kakao-1");
        then(placeRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("성공 - 처음 보는 장소면 저장해서 돌려준다")
    void register_newPlace() {
        given(placeRepository.findByExternalId("kakao-1")).willReturn(Optional.empty());
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> invocation.getArgument(0));

        PlaceRegisterResponse response = placeRegisterService.register(request());

        assertThat(response.name()).isEqualTo("성수 카페");
        then(placeRepository).should().saveAndFlush(any(Place.class));
    }

    private PlaceRegisterRequest request() {
        return new PlaceRegisterRequest(
                "kakao-1", "성수 카페", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557"), true);
    }

    private Place place(String name) {
        return Place.create("kakao-1", name, "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557"), true);
    }
}
