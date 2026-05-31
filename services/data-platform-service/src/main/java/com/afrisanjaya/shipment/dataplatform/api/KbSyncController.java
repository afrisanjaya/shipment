package com.afrisanjaya.shipment.dataplatform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.ListDataSourcesRequest;
import software.amazon.awssdk.services.bedrockagent.model.ListDataSourcesResponse;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobResponse;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base Sync", description = "Trigger Bedrock KB data source ingestion")
public class KbSyncController {

    private final BedrockAgentClient bedrockAgentClient;

    @Value("${bedrock.kb.id}")
    private String knowledgeBaseId;

    @Operation(summary = "Sync KB data source — lists data sources and starts ingestion job")
    @PostMapping("/kb/sync")
    public ResponseEntity<Map<String, Object>> syncKnowledgeBase() {
        log.info("[KB-SYNC] Starting sync for knowledgeBaseId={}", knowledgeBaseId);

        try {
            ListDataSourcesResponse listResp = bedrockAgentClient.listDataSources(
                    ListDataSourcesRequest.builder()
                            .knowledgeBaseId(knowledgeBaseId)
                            .maxResults(1)
                            .build());

            if (listResp.dataSourceSummaries().isEmpty()) {
                log.warn("[KB-SYNC] No data sources found for KB {}", knowledgeBaseId);
                return ResponseEntity.ok(Map.of(
                        "status", "NO_DATA_SOURCE",
                        "knowledgeBaseId", knowledgeBaseId,
                        "message", "No data sources configured for this KB"
                ));
            }

            String dataSourceId = listResp.dataSourceSummaries().get(0).dataSourceId();
            log.info("[KB-SYNC] Found dataSourceId={}", dataSourceId);

            StartIngestionJobResponse jobResp = bedrockAgentClient.startIngestionJob(
                    StartIngestionJobRequest.builder()
                            .knowledgeBaseId(knowledgeBaseId)
                            .dataSourceId(dataSourceId)
                            .build());

            log.info("[KB-SYNC] Ingestion job started: jobId={}", jobResp.ingestionJob().ingestionJobId());

            return ResponseEntity.ok(Map.of(
                    "status", "STARTED",
                    "knowledgeBaseId", knowledgeBaseId,
                    "dataSourceId", dataSourceId,
                    "ingestionJobId", jobResp.ingestionJob().ingestionJobId()
            ));

        } catch (Exception e) {
            log.error("[KB-SYNC] Sync failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "knowledgeBaseId", knowledgeBaseId,
                    "error", e.getMessage()
            ));
        }
    }
}
