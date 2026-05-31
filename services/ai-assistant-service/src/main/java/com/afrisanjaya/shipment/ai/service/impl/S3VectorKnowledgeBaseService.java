package com.afrisanjaya.shipment.ai.service.impl;

import com.afrisanjaya.shipment.ai.api.dto.LogisticsMatch;
import com.afrisanjaya.shipment.ai.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class S3VectorKnowledgeBaseService implements KnowledgeBaseService {

    private final BedrockAgentRuntimeAsyncClient agentRuntimeClient;

    @Value("${bedrock.kb.id}")
    private String kbId;

    @Value("${bedrock.s3vector.bucket-arn}")
    private String s3VectorBucketArn;

    @Value("${bedrock.s3vector.region}")
    private String s3VectorRegion;

    @Value("${kb.prompt.template}")
    private String kbPromptTemplate;

    public S3VectorKnowledgeBaseService(BedrockAgentRuntimeAsyncClient agentRuntimeClient) {
        this.agentRuntimeClient = agentRuntimeClient;
    }

    @Override
    public List<LogisticsMatch> search(String tenantId, String query, int topK) {
        log.info("[S3VECTOR-KB] Searching for tenant={}, query='{}', topK={}", tenantId, query, topK);

        var kbConfig = KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                .knowledgeBaseId(kbId)
                .modelArn("arn:aws:bedrock:" + s3VectorRegion + "::foundation-model/amazon.nova-lite-v1:0")
                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                .numberOfResults(topK)
                                .build())
                        .build())
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
            List<LogisticsMatch> results = new ArrayList<>();

            if (response.citations() != null) {
                for (var citation : response.citations()) {
                    if (citation.retrievedReferences() != null) {
                        for (var ref : citation.retrievedReferences()) {
                            String content = ref.content() != null && ref.content().text() != null
                                    ? ref.content().text() : "";
                            String location = ref.location() != null && ref.location().s3Location() != null
                                    ? ref.location().s3Location().uri() : "s3vector://" + s3VectorBucketArn;

                            results.add(new LogisticsMatch(
                                    UUID.nameUUIDFromBytes(content.getBytes()),
                                    extractField(content, "name"),
                                    extractField(content, "type"),
                                    extractField(content, "location"),
                                    ref.metadata() != null && ref.metadata().containsKey("score")
                                            ? Double.parseDouble(ref.metadata().get("score").asString()) : 0.0,
                                    content.length() > 200 ? content.substring(0, 200) + "..." : content
                            ));
                        }
                    }
                }
            }

            log.info("[S3VECTOR-KB] Found {} results for tenant={}", results.size(), tenantId);
            return results;
        } catch (Exception e) {
            log.error("[S3VECTOR-KB] Search failed for tenant={}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String retrieveAndGenerate(String tenantId, String query) {
        log.info("[S3VECTOR-KB] RetrieveAndGenerate for tenant={}, query='{}'", tenantId, query);

        var kbConfig = KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                .knowledgeBaseId(kbId)
                .modelArn("arn:aws:bedrock:" + s3VectorRegion + "::foundation-model/amazon.nova-lite-v1:0")
                .generationConfiguration(GenerationConfiguration.builder()
                        .promptTemplate(PromptTemplate.builder()
                                .textPromptTemplate(kbPromptTemplate)
                                .build())
                        .build())
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
            log.info("[S3VECTOR-KB] Generated answer ({} chars) for tenant={}",
                    answer.length(), tenantId);
            return answer;
        } catch (Exception e) {
            log.error("[S3VECTOR-KB] RetrieveAndGenerate failed for tenant={}: {}", tenantId, e.getMessage());
            return "Error retrieving data from knowledge base: " + e.getMessage();
        }
    }

    private String extractField(String content, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int startIdx = content.indexOf(key);
        if (startIdx == -1) {
            key = fieldName + ":";
            startIdx = content.indexOf(key);
        }
        if (startIdx == -1) return "";

        startIdx += key.length();
        while (startIdx < content.length() &&
                (content.charAt(startIdx) == '"' || content.charAt(startIdx) == ' ')) {
            startIdx++;
        }

        int endIdx = startIdx;
        while (endIdx < content.length() &&
                content.charAt(endIdx) != '"' && content.charAt(endIdx) != ','
                && content.charAt(endIdx) != '\n' && content.charAt(endIdx) != '}') {
            endIdx++;
        }

        return content.substring(startIdx, endIdx).trim();
    }
}
