package com.bready.server.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bready.server.user.domain.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {}
