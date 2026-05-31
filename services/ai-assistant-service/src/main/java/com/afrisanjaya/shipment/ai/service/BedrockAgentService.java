package com.afrisanjaya.shipment.ai.service;

import com.afrisanjaya.shipment.ai.api.dto.ChatRequest;
import com.afrisanjaya.shipment.ai.api.dto.ChatResponse;

public interface BedrockAgentService {

    ChatResponse invokeAgent(ChatRequest request);

    String retrieveFromKb(String query);

    void endSession(String sessionId);
}
