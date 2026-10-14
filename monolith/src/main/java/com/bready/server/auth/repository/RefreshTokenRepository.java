package com.bready.server.auth.repository;

import org.springframework.data.repository.CrudRepository;

import com.bready.server.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {}
