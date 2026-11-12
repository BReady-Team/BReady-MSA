-- 장소 정보
CREATE TABLE places
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    external_id VARCHAR(255)   NOT NULL,
    name        VARCHAR(255)   NOT NULL,
    address     VARCHAR(255),
    latitude    DECIMAL(10, 6) NOT NULL,
    longitude   DECIMAL(10, 6) NOT NULL,
    is_indoor   BIT(1),
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_places_external_id UNIQUE (external_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
