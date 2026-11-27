package com.bready.recommendation.recommendation.ai;

import java.util.Map;

public interface AiRerankTarget {

    String id();

    String name();

    default Map<String, Object> toPromptAttributes() {
        return Map.of(
                "id", id(),
                "name", name());
    }
}
