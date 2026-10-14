package com.bready.server.place.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bready.server.place.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByExternalId(String externalId);
}
