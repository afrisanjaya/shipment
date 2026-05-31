package com.afrisanjaya.shipment.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.*;

import java.util.List;

@Slf4j
@Component
public class AgentConfigInitializer {

    private final BedrockAgentClient agentClient;
    private final String agentId;

    public AgentConfigInitializer(@Value("${bedrock.agent.id}") String agentId) {
        this.agentId = agentId;
        this.agentClient = BedrockAgentClient.builder()
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyKbPromptConfig() {
        try {
            var current = agentClient.getAgent(GetAgentRequest.builder().agentId(agentId).build());
            var prompts = current.agent().promptOverrideConfiguration();
            if (prompts != null && prompts.promptConfigurations().stream()
                    .anyMatch(p -> "OVERRIDDEN".equals(p.promptCreationMode())
                            && p.inferenceConfiguration() != null
                            && p.inferenceConfiguration().maximumLength() >= 4096)) {
                log.info("[CONFIG] Prompt override already configured (maxLength>=4096), skipping");
                return;
            }
        } catch (Exception e) {
            log.debug("[CONFIG] Could not check current config: {}", e.getMessage());
        }

        log.info("[CONFIG] Applying KB response generation prompt override...");

        var kbConfig = PromptConfiguration.builder()
                .promptType(PromptType.KNOWLEDGE_BASE_RESPONSE_GENERATION)
                .promptState(PromptState.ENABLED)
                .promptCreationMode("OVERRIDDEN")
                .basePromptTemplate("""
                    {
                        "system": "Agent Description:\n$instruction$\n\nAlways follow these instructions:\n$ask_user_missing_information$\n$knowledge_base_additional_guideline$\n$memory_guideline$\n$memory_content$\n$memory_action_guideline$\n$code_interpreter_files$\n$prompt_session_attributes$\n",
                        "messages": [
                            {"role": "user", "content": [{"text": "$query$"}]},
                            {"role": "assistant", "content": [{"text": "$agent_scratchpad$"}]},
                            {"role": "assistant", "content": [{"text": "<answer>$search_results$</answer>"}]}
                        ]
                    }
                    """)
                .inferenceConfiguration(InferenceConfiguration.builder()
                        .maximumLength(4096)
                        .temperature(1.0f)
                        .topP(1.0f)
                        .topK(1)
                        .stopSequences(List.of("</answer>"))
                        .build())
                .parserMode("DEFAULT")
                .build();

        var request = UpdateAgentRequest.builder()
                .agentId(agentId)
                .promptOverrideConfiguration(PromptOverrideConfiguration.builder()
                        .promptConfigurations(List.of(kbConfig))
                        .build())
                .build();

        try {
            agentClient.updateAgent(request);
            log.info("[CONFIG] KB response generation prompt updated — maximumLength=4096");
        } catch (Exception e) {
            log.warn("[CONFIG] Failed to update agent prompt config: {}", e.getMessage());
        }
    }
}
