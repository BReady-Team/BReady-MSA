package com.bready.server.global.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    private final LoggingAspect loggingAspect = new LoggingAspect();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    @BeforeEach
    void attachAppender() {
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getDeclaringTypeName()).willReturn("com.bready.server.auth.service.AuthService");
        given(signature.getName()).willReturn("login");
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("성공 - 서비스 시작 로그는 DEBUG로 남고 인자를 읽지 않는다")
    void logBefore_doesNotLogArguments() {
        loggingAspect.logBefore(joinPoint);

        then(joinPoint).should(never()).getArgs();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(appender.list.get(0).getFormattedMessage())
                .isEqualTo("[START] com.bready.server.auth.service.AuthService.login()");
    }

    @Test
    @DisplayName("성공 - 서비스 종료 로그는 DEBUG로 남는다")
    void logAfter_logsAtDebug() {
        loggingAspect.logAfter(joinPoint);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    @DisplayName("성공 - 서비스에서 예외가 나면 ERROR로 예외 객체와 함께 남는다")
    void logException_logsAtErrorWithThrowable() {
        IllegalStateException exception = new IllegalStateException("실패 원인");

        loggingAspect.logException(joinPoint, exception);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.get(0).getThrowableProxy().getMessage()).isEqualTo("실패 원인");
    }
}
