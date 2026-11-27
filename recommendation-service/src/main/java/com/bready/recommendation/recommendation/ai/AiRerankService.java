package com.bready.recommendation.recommendation.ai;

import java.util.List;

public interface AiRerankService {

    AiRerankResult rerank(String context, List<? extends AiRerankTarget> candidates);
}
