# 이미지 안에서 빌드
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

# Gradle 캐시를 빌드 사이에 재사용
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
