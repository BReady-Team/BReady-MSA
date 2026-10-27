package com.bready.catalog.place.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bready.catalog.place.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByExternalId(String externalId);
}
