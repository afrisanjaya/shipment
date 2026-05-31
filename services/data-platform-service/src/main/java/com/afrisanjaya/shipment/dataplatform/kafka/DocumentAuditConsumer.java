package com.afrisanjaya.shipment.dataplatform.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.dataplatform.domain.entity.DocumentAuditResult;
import com.afrisanjaya.shipment.dataplatform.domain.repository.DocumentAuditResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAuditConsumer {

    private final DocumentAuditResultRepository auditRepository;
    private final S3Client s3Client;
    private final String s3BucketName;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String LOGISTICS_URL = "http://logistics-service:8081";
    private static final String BILLING_URL = "http://billing-service:8082";

    private static final Pattern INVOICE_NO = Pattern.compile(
            "INV[-/]?\\d{4,10}", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:Total|Jumlah|Amount)[:\\s]*Rp?\\s*([\\d,.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIPMENT_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    @KafkaListener(topics = "document-uploaded", groupId = "dataplatform-audit-consumer")
    public void consume(String message) {
        UUID uploadId = null;
        UUID tenantId = null;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            uploadId = UUID.fromString(event.get("uploadId").toString());
            tenantId = UUID.fromString(event.get("tenantId").toString());
            String objectKey = event.get("objectKey").toString();
            String documentType = Optional.ofNullable(event.get("documentType"))
                    .map(Object::toString).orElse("INVOICE");

            log.info("[AUDIT] Processing document: uploadId={} tenantId={} type={}",
                    uploadId, tenantId, documentType);

            byte[] fileBytes = downloadFromS3(objectKey);

            boolean isWord = objectKey.toLowerCase().endsWith(".docx");
            String extractedText = isWord ? extractTextFromWord(fileBytes) : extractText(fileBytes);

            storeExtractedJsonToS3(tenantId, uploadId, documentType, objectKey, extractedText);

            String invoiceNo = extractPattern(INVOICE_NO, extractedText).orElse("UNKNOWN");
            BigDecimal invoiceAmount = extractAmount(extractedText).orElse(BigDecimal.ZERO);
            Optional<UUID> shipmentId = extractShipmentId(extractedText);

            Map<String, Object> compliance = new LinkedHashMap<>();
            Map<String, Object> discrepancies = new LinkedHashMap<>();

            checkShipmentMatch(shipmentId, compliance, discrepancies);
            checkPaymentMatch(invoiceAmount, compliance, discrepancies);

            String auditStatus = discrepancies.isEmpty() ? "COMPLIANT" : "DISCREPANCY";

            DocumentAuditResult result = DocumentAuditResult.builder()
                    .uploadId(uploadId)
                    .tenantId(tenantId)
                    .documentType(documentType)
                    .extractedText(truncate(extractedText, 10000))
                    .auditStatus(auditStatus)
                    .complianceChecks(compliance)
                    .discrepancyDetails(discrepancies.isEmpty() ? null : discrepancies)
                    .auditedAt(OffsetDateTime.now())
                    .build();

            auditRepository.save(result);
            log.info("[AUDIT] Document {} — status={} invoiceNo={} amount={}",
                    uploadId, auditStatus, invoiceNo, invoiceAmount);

        } catch (Exception e) {
            log.error("[AUDIT] Failed for uploadId={}: {}", uploadId, e.getMessage(), e);
            if (uploadId != null && tenantId != null) {
                try {
                    DocumentAuditResult errorResult = DocumentAuditResult.builder()
                            .uploadId(uploadId)
                            .tenantId(tenantId)
                            .documentType("UNKNOWN")
                            .auditStatus("ERROR")
                            .discrepancyDetails(Map.of("error", e.getMessage()))
                            .auditedAt(OffsetDateTime.now())
                            .build();
                    auditRepository.save(errorResult);
                } catch (Exception ignored) {
                    log.error("[AUDIT] Failed to persist error state", ignored);
                }
            }
        }
    }


    private byte[] downloadFromS3(String objectKey) {
        log.debug("[AUDIT] Downloading PDF from S3: bucket={}, key={}", s3BucketName, objectKey);
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(s3BucketName)
                        .key(objectKey)
                        .build());
        return response.asByteArray();
    }


    private String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }


    private String extractTextFromWord(byte[] docxBytes) throws Exception {
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(docxBytes);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }


    private void storeExtractedJsonToS3(UUID tenantId, UUID uploadId, String documentType, String objectKey, String extractedText) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tenantId", tenantId.toString());
            data.put("uploadId", uploadId.toString());
            data.put("documentType", documentType);
            data.put("originalKey", objectKey);
            data.put("extractedAt", OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
            data.put("extractedText", extractedText);

            String json = objectMapper.writeValueAsString(data);
            String destinationKey = "reports/extracted/" + tenantId + "/" + uploadId + ".json";

            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(destinationKey)
                    .contentType("application/json")
                    .build(),
                    RequestBody.fromBytes(json.getBytes("UTF-8")));
            log.info("[AUDIT] Stored extracted document JSON in S3: key={}", destinationKey);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to store extracted JSON to S3: {}", e.getMessage(), e);
        }
    }


    private void checkShipmentMatch(Optional<UUID> shipmentId,
                                     Map<String, Object> compliance,
                                     Map<String, Object> discrepancies) {
        if (shipmentId.isEmpty()) {
            compliance.put("shipmentCheck", "SKIPPED — no shipment ID found in document");
            return;
        }

        try {
            String url = LOGISTICS_URL + "/api/v1/shipments/" + shipmentId.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> shipment = restTemplate.getForObject(url, Map.class);

            if (shipment != null && !shipment.containsKey("error")) {
                compliance.put("shipmentCheck", "MATCHED");
                compliance.put("shipmentStatus", shipment.getOrDefault("status", "UNKNOWN"));
            } else {
                discrepancies.put("shipmentCheck", "Shipment not found: " + shipmentId.get());
            }
        } catch (Exception e) {
            compliance.put("shipmentCheck", "SKIPPED — logistics-service unreachable: "
                    + e.getMessage());
        }
    }

    private void checkPaymentMatch(BigDecimal invoiceAmount,
                                    Map<String, Object> compliance,
                                    Map<String, Object> discrepancies) {
        if (invoiceAmount.compareTo(BigDecimal.ZERO) == 0) {
            compliance.put("paymentCheck", "SKIPPED — no amount extracted from document");
            return;
        }

        compliance.put("paymentCheck", "PENDING_MANUAL_REVIEW");
        compliance.put("extractedAmount", invoiceAmount.toPlainString());
        compliance.put("note", "Wallet service query not yet wired — verify manually");
    }


    private Optional<String> extractPattern(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    private Optional<BigDecimal> extractAmount(String text) {
        Matcher m = AMOUNT.matcher(text);
        if (m.find()) {
            try {
                String cleaned = m.group(1).replace(",", "").replace(".", "");
                return Optional.of(new BigDecimal(cleaned));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> extractShipmentId(String text) {
        Matcher m = SHIPMENT_ID.matcher(text);
        return m.find() ? Optional.of(UUID.fromString(m.group())) : Optional.empty();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "... [truncated]";
    }
}
