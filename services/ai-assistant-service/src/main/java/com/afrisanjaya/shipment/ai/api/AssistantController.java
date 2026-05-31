package com.afrisanjaya.shipment.ai.api;

import com.afrisanjaya.shipment.ai.api.dto.ChatRequest;
import com.afrisanjaya.shipment.ai.api.dto.ChatResponse;
import com.afrisanjaya.shipment.ai.service.BedrockAgentService;
import com.afrisanjaya.shipment.ai.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "AI Assistant API", description = "Bedrock Agent + S3 Vector KB endpoints for supply chain & logistics")
public class AssistantController {

    private final BedrockAgentService bedrockAgentService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "Execute a logistics task via Agent orchestration")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = bedrockAgentService.invokeAgent(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Query knowledge base directly for warehouse, policy, or supplier info")
    @PostMapping("/kb-search")
    public ResponseEntity<ChatResponse> searchKb(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        String tid = tenantId != null ? tenantId : request.tenantId() != null ? request.tenantId() : "default";
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String answer = knowledgeBaseService.retrieveAndGenerate(tid, request.message());

        return ResponseEntity.ok(new ChatResponse(
                sessionId, answer, null, Collections.emptyList(), "KB"));
    }

    @Operation(summary = "End a chat session")
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> endSession(
            @PathVariable String sessionId) {

        bedrockAgentService.endSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
