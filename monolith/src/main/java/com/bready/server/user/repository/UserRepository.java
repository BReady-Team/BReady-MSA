package com.bready.server.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bready.server.user.domain.User;
import com.bready.server.user.domain.UserAuthProvider;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByAuthProviderAndProviderUserId(UserAuthProvider authProvider, String providerUserId);

    @Query("""
        select u from User u
        left join fetch u.userProfile
        where u.id = :userId
    """)
    Optional<User> findByIdWithProfile(@Param("userId") Long userId);
}
