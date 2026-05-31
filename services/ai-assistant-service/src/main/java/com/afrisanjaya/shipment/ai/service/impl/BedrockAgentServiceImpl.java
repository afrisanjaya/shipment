package com.afrisanjaya.shipment.ai.service.impl;

import com.afrisanjaya.shipment.ai.api.dto.ChatRequest;
import com.afrisanjaya.shipment.ai.api.dto.ChatResponse;
import com.afrisanjaya.shipment.ai.config.BedrockProperties;
import com.afrisanjaya.shipment.ai.service.BedrockAgentService;
import com.afrisanjaya.shipment.ai.service.LogisticsToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class BedrockAgentServiceImpl implements BedrockAgentService {

    private final BedrockAgentRuntimeAsyncClient agentRuntimeClient;
    private final LogisticsToolService logisticsToolService;
    private final BedrockProperties bedrockProperties;

    @Value("${AWS_REGION}")
    private String region;

    public BedrockAgentServiceImpl(BedrockAgentRuntimeAsyncClient agentRuntimeClient,
                                   LogisticsToolService logisticsToolService,
                                   BedrockProperties bedrockProperties) {
        this.agentRuntimeClient = agentRuntimeClient;
        this.logisticsToolService = logisticsToolService;
        this.bedrockProperties = bedrockProperties;
    }

    @Override
    public ChatResponse invokeAgent(ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String tenantId = request.tenantId() != null ? request.tenantId() : "default";
        String inputText = request.message();

        log.info("[AGENT] tenantId={} sessionId={} message='{}'", tenantId, sessionId, request.message());

        for (int round = 0; round < 3; round++) {
            log.info("[AGENT] Round {}/3 — invoking Bedrock Agent", round + 1);

            Map<String, String> sessionAttrs = new HashMap<>();
            sessionAttrs.put("tenantId", tenantId);

            SessionState sessionState = SessionState.builder()
                    .sessionAttributes(sessionAttrs)
                    .build();

            InvokeAgentRequest invokeRequest = InvokeAgentRequest.builder()
                    .agentId(bedrockProperties.agent().id())
                    .agentAliasId(bedrockProperties.agent().aliasId())
                    .sessionId(sessionId)
                    .sessionState(sessionState)
                    .inputText(inputText)
                    .enableTrace(true)
                    .build();

            AtomicReference<String> responseText = new AtomicReference<>("");
            AtomicReference<ReturnControlPayload> returnControl = new AtomicReference<>();
            AtomicReference<String> returnControlInvocationId = new AtomicReference<>("");
            AtomicBoolean hasReturnControl = new AtomicBoolean(false);
            AtomicReference<Integer> totalInputTokens = new AtomicReference<>(0);
            AtomicReference<Integer> totalOutputTokens = new AtomicReference<>(0);

            InvokeAgentResponseHandler handler = InvokeAgentResponseHandler.builder()
                    .subscriber(InvokeAgentResponseHandler.Visitor.builder()
                            .onChunk(chunk -> {
                                if (chunk.bytes() != null) {
                                    String text = chunk.bytes().asUtf8String();
                                    log.debug("[AGENT] Chunk: {}", text);
                                    responseText.updateAndGet(c -> c + text);
                                }
                            })
                            .onReturnControl(rc -> {
                                log.info("[AGENT] Agent returned control — invocationId={} inputs={}",
                                        rc.invocationId(), rc.invocationInputs().size());
                                returnControl.set(rc);
                                returnControlInvocationId.set(rc.invocationId());
                                hasReturnControl.set(true);
                            })
                            .onTrace(tp -> {
                                try {
                                    var t = tp.trace();
                                    extractTokenUsage(t.orchestrationTrace(), "orch", totalInputTokens, totalOutputTokens);
                                } catch (Exception e) {
                                    log.debug("[TRACE] parse error: {}", e.getMessage());
                                }
                            })
                            .build())
                    .build();

            try {
                CompletableFuture<Void> future = agentRuntimeClient.invokeAgent(invokeRequest, handler);
                future.join();
            } catch (Exception e) {
                log.error("[AGENT] Bedrock invocation failed: {}", e.getMessage());
                if (e.getMessage().contains("rate is too high") || e.getMessage().contains("TooManyRequests")) {
                    return new ChatResponse(sessionId,
                            "The system is busy. Please try again in a few seconds.",
                            null, Collections.emptyList(), "AGENT");
                }
                return new ChatResponse(sessionId,
                        "An AI system error occurred. Please try again.",
                        null, Collections.emptyList(), "AGENT");
            }

            if (!hasReturnControl.get()) {
                String response = responseText.get().isEmpty() ? "No response from Agent." : responseText.get();
                log.info("[AGENT] Final response ({} chars, input={} output={} tokens): {}",
                        response.length(), totalInputTokens.get(), totalOutputTokens.get(),
                        response.length() > 200 ? response.substring(0, 200) + "..." : response);
                return new ChatResponse(sessionId, response, null, Collections.emptyList(), "AGENT");
            }

            List<InvocationResultMember> results = buildInvocationResults(tenantId, returnControl.get());
            sessionState = SessionState.builder()
                    .sessionAttributes(sessionAttrs)
                    .invocationId(returnControlInvocationId.get())
                    .returnControlInvocationResults(results)
                    .build();

            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        log.warn("[AGENT] Max rounds reached for sessionId={}", sessionId);
        return new ChatResponse(sessionId, "Sorry, I could not complete your request.",
                null, Collections.emptyList(), "AGENT");
    }


    private List<InvocationResultMember> buildInvocationResults(String tenantId, ReturnControlPayload rc) {
        List<InvocationResultMember> results = new ArrayList<>();
        for (InvocationInputMember member : rc.invocationInputs()) {
            String actionGroup;
            String function;
            String body;

            if (member.functionInvocationInput() != null) {
                var fn = member.functionInvocationInput();
                actionGroup = fn.actionGroup();
                function = fn.function();
                body = dispatchFunctionCall(tenantId, fn);
            } else if (member.apiInvocationInput() != null) {
                var api = member.apiInvocationInput();
                actionGroup = api.actionGroup();
                function = api.apiPath();
                body = dispatchApiCall(tenantId, api);
            } else {
                continue;
            }

            FunctionResult functionResult = FunctionResult.builder()
                    .actionGroup(actionGroup)
                    .function(function)
                    .responseBody(Map.of("TEXT", ContentBody.builder().body(body).build()))
                    .build();

            results.add(InvocationResultMember.builder().functionResult(functionResult).build());
        }
        log.info("[AGENT] Built {} invocation results for return control", results.size());
        return results;
    }

    private String dispatchFunctionCall(String tenantId, FunctionInvocationInput fn) {
        Map<String, String> params = fn.parameters().stream()
                .reduce(new HashMap<>(),
                        (map, p) -> { map.put(p.name(), p.value()); return map; },
                        (a, b) -> { a.putAll(b); return a; });

        log.info("[ACTION] tenant={} function={} params={}", tenantId, fn.function(), params);

        return switch (fn.function()) {
            case "getShipmentStatus"   -> logisticsToolService.getShipmentStatus(
                    params.getOrDefault("shipmentId", ""));
            case "checkInventory"      -> logisticsToolService.checkInventory(
                    params.getOrDefault("skuId", ""), params.getOrDefault("warehouseId", ""));
            case "getShipmentDetails"  -> logisticsToolService.getShipmentDetails(
                    params.getOrDefault("shipmentId", ""));
            case "checkSlaRisk"        -> logisticsToolService.checkSlaRisk(
                    params.getOrDefault("shipmentId", ""));
            case "getSensorHealth"     -> logisticsToolService.getSensorHealth(
                    params.getOrDefault("shipmentId", ""), params.getOrDefault("sensorType", ""));
            default -> {
                log.warn("[ACTION] Unknown function: {}", fn.function());
                yield "{\"error\": \"Unknown function: " + fn.function() + "\"}";
            }
        };
    }

    private String dispatchApiCall(String tenantId, ApiInvocationInput api) {
        Map<String, String> params = new HashMap<>();
        if (api.parameters() != null) {
            for (var p : api.parameters()) {
                params.put(p.name(), p.value());
            }
        }
        log.info("[ACTION] tenant={} API call: {} {} params={}", tenantId, api.httpMethod(), api.apiPath(), params);

        return switch (api.apiPath()) {
            case "/shipments/status"  -> logisticsToolService.getShipmentStatus(
                    params.getOrDefault("shipmentId", ""));
            case "/inventory/check"   -> logisticsToolService.checkInventory(
                    params.getOrDefault("skuId", ""), params.getOrDefault("warehouseId", ""));
            case "/shipments/details" -> logisticsToolService.getShipmentDetails(
                    params.getOrDefault("shipmentId", ""));
            case "/shipments/sla"     -> logisticsToolService.checkSlaRisk(
                    params.getOrDefault("shipmentId", ""));
            case "/shipments/sensors" -> logisticsToolService.getSensorHealth(
                    params.getOrDefault("shipmentId", ""), params.getOrDefault("sensorType", ""));
            default -> "{\"error\": \"Unknown API path: " + api.apiPath() + "\"}";
        };
    }


    private void extractTokenUsage(OrchestrationTrace trace, String label,
            AtomicReference<Integer> totalIn, AtomicReference<Integer> totalOut) {
        if (trace == null || trace.modelInvocationOutput() == null) return;
        try {
            var metadata = trace.modelInvocationOutput().metadata();
            if (metadata == null || metadata.usage() == null) return;
            int in = metadata.usage().inputTokens();
            int out = metadata.usage().outputTokens();
            totalIn.updateAndGet(t -> t + in);
            totalOut.updateAndGet(t -> t + out);
            log.info("[TOKEN] {} — input={} output={}", label, in, out);
        } catch (Exception e) {
            log.debug("[TOKEN] {} — not available ({})", label, e.getMessage());
        }
    }


    @Override
    public String retrieveFromKb(String query) {
        log.info("[KB] RetrieveAndGenerate via S3 Vector: '{}'", query);

        var kbConfig = KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                .knowledgeBaseId(bedrockProperties.kb().id())
                .modelArn("arn:aws:bedrock:" + region + "::foundation-model/amazon.nova-lite-v1:0")
                .build();

        var retrieveConfig = RetrieveAndGenerateConfiguration.builder()
                .knowledgeBaseConfiguration(kbConfig)
                .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                .build();

        var request = RetrieveAndGenerateRequest.builder()
                .input(RetrieveAndGenerateInput.builder().text(query).build())
                .retrieveAndGenerateConfiguration(retrieveConfig)
                .build();

        try {
            var response = agentRuntimeClient.retrieveAndGenerate(request).join();
            String answer = response.output().text();
            log.info("[KB] Answer ({} chars): {}", answer.length(),
                    answer.length() > 300 ? answer.substring(0, 300) + "..." : answer);
            return answer;
        } catch (Exception e) {
            log.error("[KB] RetrieveAndGenerate failed: {}", e.getMessage());
            return "KB query error: " + e.getMessage();
        }
    }

    @Override
    public void endSession(String sessionId) {
        log.info("[AGENT] Ending session {}", sessionId);
    }
}
