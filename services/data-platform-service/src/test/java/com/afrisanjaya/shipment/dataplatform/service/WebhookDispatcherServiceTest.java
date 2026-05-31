package com.afrisanjaya.shipment.dataplatform.service;

import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookDeliveryLog;
import com.afrisanjaya.shipment.dataplatform.domain.entity.WebhookSubscription;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookDeliveryLogRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherServiceTest {

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;

    @Mock
    private WebhookDeliveryLogRepository deliveryLogRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WebhookDispatcherService webhookDispatcherService;

    @Test
    void dispatch_whenNoSubscriptions_thenDoesNotCallRestOrSaveLogs() {
        UUID tenantId = UUID.randomUUID();
        given(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).willReturn(List.of());

        webhookDispatcherService.dispatch(tenantId, "data.ingested", Map.of("key", "val"));

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void dispatch_whenSubscriptionEventTypesDoNotMatch_thenSkipsDelivery() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .callbackUrl("http://client.com/webhook")
                .eventTypes("sensor.reading")
                .isActive(true)
                .build();
        given(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).willReturn(List.of(sub));

        webhookDispatcherService.dispatch(tenantId, "data.ingested", Map.of("key", "val"));

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void dispatch_whenDeliverySucceedsFirstTry_savesSuccessLog() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .callbackUrl("http://client.com/webhook")
                .eventTypes("data.ingested")
                .isActive(true)
                .build();
        given(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).willReturn(List.of(sub));

        given(deliveryLogRepository.save(any(WebhookDeliveryLog.class))).willAnswer(inv -> inv.getArgument(0));
        given(restTemplate.postForEntity(eq("http://client.com/webhook"), any(), eq(String.class)))
                .willReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        webhookDispatcherService.dispatch(tenantId, "data.ingested", Map.of("data", "yes"));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, atLeastOnce()).save(captor.capture());
        
        WebhookDeliveryLog finalLog = captor.getValue();
        assertThat(finalLog.getDeliveryStatus()).isEqualTo("SUCCESS");
        assertThat(finalLog.getAttemptCount()).isEqualTo(1);
        assertThat(finalLog.getDeliveredAt()).isNotNull();
    }

    @Test
    void dispatch_whenDeliveryFailsThenSucceedsOnSecondTry_savesSuccessLogWithTwoAttempts() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .callbackUrl("http://client.com/webhook")
                .eventTypes("*")
                .isActive(true)
                .build();
        given(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).willReturn(List.of(sub));
        given(deliveryLogRepository.save(any(WebhookDeliveryLog.class))).willAnswer(inv -> inv.getArgument(0));

        given(restTemplate.postForEntity(eq("http://client.com/webhook"), any(), eq(String.class)))
                .willThrow(new RestClientException("Connection Timeout"))
                .willReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        webhookDispatcherService.dispatch(tenantId, "data.ingested", Map.of("data", "yes"));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, atLeastOnce()).save(captor.capture());
        
        List<WebhookDeliveryLog> logs = captor.getAllValues();
        WebhookDeliveryLog finalLog = logs.get(logs.size() - 1);
        assertThat(finalLog.getDeliveryStatus()).isEqualTo("SUCCESS");
        assertThat(finalLog.getAttemptCount()).isEqualTo(2);
        assertThat(finalLog.getLastErrorMessage()).isEqualTo("Connection Timeout");
    }

    @Test
    void dispatch_whenAllRetriesFail_savesFailedLog() {
        UUID tenantId = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .callbackUrl("http://client.com/webhook")
                .eventTypes("data.ingested")
                .isActive(true)
                .build();
        given(subscriptionRepository.findByTenantIdAndIsActiveTrue(tenantId)).willReturn(List.of(sub));
        given(deliveryLogRepository.save(any(WebhookDeliveryLog.class))).willAnswer(inv -> inv.getArgument(0));

        given(restTemplate.postForEntity(eq("http://client.com/webhook"), any(), eq(String.class)))
                .willThrow(new RestClientException("Gateway Timeout"));

        webhookDispatcherService.dispatch(tenantId, "data.ingested", Map.of("data", "yes"));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, atLeastOnce()).save(captor.capture());
        
        List<WebhookDeliveryLog> logs = captor.getAllValues();
        WebhookDeliveryLog finalLog = logs.get(logs.size() - 1);
        assertThat(finalLog.getDeliveryStatus()).isEqualTo("FAILED");
        assertThat(finalLog.getAttemptCount()).isEqualTo(3);
        assertThat(finalLog.getLastErrorMessage()).isEqualTo("Gateway Timeout");
        assertThat(finalLog.getNextRetryAt()).isNull();
    }
}
