package com.afrisanjaya.shipment.ai.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class HealthController {

    private final RestTemplate restTemplate;
    private final String logisticsServiceUrl;
    private final String billingServiceUrl;

    public HealthController(RestTemplate restTemplate,
            @Value("${logistics.service.url:http://localhost:8081}") String logisticsServiceUrl,
            @Value("${billing.service.url:http://localhost:8082}") String billingServiceUrl) {
        this.restTemplate = restTemplate;
        this.logisticsServiceUrl = logisticsServiceUrl;
        this.billingServiceUrl = billingServiceUrl;
    }

    @Operation(summary = "System-wide health check — all services concurrently")
    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        Instant start = Instant.now();
        Map<String, Object> result = new LinkedHashMap<>();

        var logistics = check("logistics-service", logisticsServiceUrl + "/actuator/health");
        var billing = check("billing-service", billingServiceUrl + "/actuator/health");
        var self = check("ai-assistant-service", "http://localhost:8083/actuator/health");

        boolean allUp = true;
        for (var f : new CompletableFuture[]{logistics, billing, self}) {
            try {
                Map<String, Object> res = (Map<String, Object>) f.get(5, TimeUnit.SECONDS);
                result.put((String) res.get("name"), res.get("status"));
                if (!"UP".equals(res.get("status"))) allUp = false;
            } catch (Exception e) {
                allUp = false;
            }
        }

        result.put("overall", allUp ? "UP" : "DOWN");
        result.put("responseTimeMs", Duration.between(start, Instant.now()).toMillis());
        return ResponseEntity.ok(result);
    }

    private CompletableFuture<Map<String, Object>> check(String name, String url) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", name);
            try {
                var resp = restTemplate.getForEntity(url, Map.class);
                var body = resp.getBody();
                r.put("status", body != null ? body.getOrDefault("status", "DOWN") : "DOWN");
            } catch (Exception e) {
                r.put("status", "DOWN");
                r.put("error", e.getMessage().length() > 100
                        ? e.getMessage().substring(0, 100) : e.getMessage());
            }
            return r;
        });
    }
}
