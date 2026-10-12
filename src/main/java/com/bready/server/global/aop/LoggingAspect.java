package com.bready.server.global.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Before("execution(* com.bready.server..service..*(..))")
    public void logBefore(JoinPoint joinPoint) {
        log.debug(
                "[START] {}.{}()",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
    }

    @After("execution(* com.bready.server..service..*(..))")
    public void logAfter(JoinPoint joinPoint) {
        log.debug(
                "[ END ] {}.{}()",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
    }

    @AfterThrowing(pointcut = "execution(* com.bready.server..service..*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        log.error(
                "[ERROR] {}.{}() => {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex.getMessage(),
                ex);
    }
}
