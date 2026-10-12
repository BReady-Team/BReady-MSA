package com.bready.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bready.server.support.IntegrationTestSupport;

class BReadyApplicationTests extends IntegrationTestSupport {

    @Test
    @DisplayName("성공 - 실제 키 없이 MySQL·Redis 컨테이너만으로 앱 전체가 기동")
    void contextLoads() {}
}
