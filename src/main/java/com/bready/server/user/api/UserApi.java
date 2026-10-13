package com.bready.server.user.api;

import java.util.Optional;

public interface UserApi {

    Optional<UserSummary> findUserSummary(Long userId);
}
