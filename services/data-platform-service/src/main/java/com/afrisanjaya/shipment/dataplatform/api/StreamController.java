package com.afrisanjaya.shipment.dataplatform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Data Stream API", description = "Server-sent event stream for tenant data ingestion")
public class StreamController {

    private static final long STREAM_TIMEOUT_MILLIS = 300_000L;

    private final SseEmitterRegistry sseEmitterRegistry;

    @Operation(summary = "Stream tenant data ingestion events")
    @GetMapping(value = "/{tenantId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTenantEvents(@PathVariable UUID tenantId) {
        return sseEmitterRegistry.register(tenantId, STREAM_TIMEOUT_MILLIS);
    }
}
