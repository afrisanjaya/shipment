package com.afrisanjaya.shipment.dataplatform;

import com.afrisanjaya.shipment.dataplatform.api.dto.CreateTenantRequest;
import com.afrisanjaya.shipment.dataplatform.api.dto.IngestDataRequest;
import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantDataRepository;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DataPlatformIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("platform_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate;
    private final TenantRepository tenantRepository;
    private final TenantDataRepository tenantDataRepository;

    DataPlatformIntegrationTest(
            TestRestTemplate restTemplate,
            TenantRepository tenantRepository,
            TenantDataRepository tenantDataRepository) {
        this.restTemplate = restTemplate;
        this.tenantRepository = tenantRepository;
        this.tenantDataRepository = tenantDataRepository;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void cleanUp() {
        tenantDataRepository.deleteAll();
        tenantRepository.deleteAll();
    }


    @Test
    void ingest_withoutApiKey_returns401Unauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        IngestDataRequest request = new IngestDataRequest("test",
                Map.of("hello", "world"));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/00000000-0000-0000-0000-000000000001/data",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    @Test
    void ingest_withInvalidApiKey_returns401Unauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("tp_bogus-key-that-does-not-exist");
        IngestDataRequest request = new IngestDataRequest("test",
                Map.of("hello", "world"));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/00000000-0000-0000-0000-000000000001/data",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    @Test
    void createTenant_doesNotRequireApiKey_returns201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateTenantRequest request = new CreateTenantRequest("Test Corp", "PRO");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("name", "Test Corp");
        assertThat(response.getBody()).containsEntry("plan", "PRO");
        assertThat(response.getBody().get("apiKey").toString()).startsWith("tp_");
    }


    @Test
    void createTenant_thenIngestData_thenQueryReturnsIt() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateTenantRequest createReq = new CreateTenantRequest("Pipeline Corp", "ENTERPRISE");

        ResponseEntity<Map> createRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                Map.class
        );

        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = createRes.getBody().get("apiKey").toString();
        String tenantId = createRes.getBody().get("id").toString();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(apiKey);
        IngestDataRequest ingestReq = new IngestDataRequest("shipment",
                Map.of("status", "IN_TRANSIT", "courier", "JNE"));

        ResponseEntity<Map> ingestRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/" + tenantId + "/data",
                HttpMethod.POST,
                new HttpEntity<>(ingestReq, authHeaders),
                Map.class
        );

        assertThat(ingestRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> queryRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/" + tenantId + "/data?dataType=shipment",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(queryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queryRes.getBody()).isNotNull();
        assertThat(queryRes.getBody()).containsEntry("totalElements", 1);
        assertThat(queryRes.getBody().get("data").toString()).contains("IN_TRANSIT");
    }


    @Test
    void query_withSpecialCharDataType_returnsFiltersCorrectly() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateTenantRequest createReq = new CreateTenantRequest("Special Corp", "FREE");

        ResponseEntity<Map> createRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                Map.class
        );

        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = createRes.getBody().get("apiKey").toString();
        String tenantId = createRes.getBody().get("id").toString();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(apiKey);
        IngestDataRequest ingestReq = new IngestDataRequest("gps+v2/sensor",
                Map.of("lat", -6.2));

        ResponseEntity<Map> ingestRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/" + tenantId + "/data",
                HttpMethod.POST,
                new HttpEntity<>(ingestReq, authHeaders),
                Map.class
        );

        assertThat(ingestRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> queryRes = restTemplate.exchange(
                baseUrl() + "/api/v1/tenants/" + tenantId + "/data?dataType=gps+v2/sensor",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(queryRes.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
