package com.afrisanjaya.shipment.dataplatform.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitterRegistry {

    private static final String DATA_INGESTED_EVENT = "tenant-data-ingested";

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByTenant =
            new ConcurrentHashMap<>();

    public SseEmitter register(UUID tenantId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emittersByTenant.computeIfAbsent(tenantId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(throwable -> remove(tenantId, emitter));

        log.debug("SSE emitter registered: tenantId={}", tenantId);
        return emitter;
    }

    public void broadcast(UUID tenantId, Object payload) {
        List<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        emitters.forEach(emitter -> sendOrRemove(tenantId, emitter, payload));
    }

    private void sendOrRemove(UUID tenantId, SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(DATA_INGESTED_EVENT)
                    .data(payload));
        } catch (IOException | IllegalStateException e) {
            log.debug("Removing closed SSE emitter: tenantId={}", tenantId);
            remove(tenantId, emitter);
        }
    }

    private void remove(UUID tenantId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByTenant.remove(tenantId, emitters);
        }
    }
}
