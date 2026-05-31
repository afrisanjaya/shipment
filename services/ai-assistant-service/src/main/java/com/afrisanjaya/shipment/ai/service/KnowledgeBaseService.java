package com.afrisanjaya.shipment.ai.service;

import com.afrisanjaya.shipment.ai.api.dto.LogisticsMatch;
import java.util.List;

public interface KnowledgeBaseService {
    List<LogisticsMatch> search(String tenantId, String query, int topK);

    String retrieveAndGenerate(String tenantId, String query);
}
