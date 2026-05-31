package com.afrisanjaya.shipment.dataplatform.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.dataplatform.domain.entity.DocumentAuditResult;
import com.afrisanjaya.shipment.dataplatform.domain.repository.DocumentAuditResultRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentAuditConsumerTest {

    @Mock
    private DocumentAuditResultRepository auditRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private RestTemplate restTemplate;

    private DocumentAuditConsumer consumer;
    private final String s3BucketName = "test-bucket";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID tenantId;
    private UUID uploadId;
    private UUID shipmentId;

    @BeforeEach
    void setUp() {
        consumer = new DocumentAuditConsumer(
                auditRepository,
                s3Client,
                s3BucketName,
                restTemplate,
                objectMapper
        );
        tenantId = UUID.randomUUID();
        uploadId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
    }

    @Test
    void consume_whenPdfDocument_thenParsesSuccessfullyAndSavesToS3() throws Exception {
        String invoiceText = "Invoice No: INV-1002\nTotal Amount: Rp 250.000\nShipment ID: " + shipmentId;
        byte[] pdfBytes = createMockPdf(invoiceText);

        ResponseBytes<GetObjectResponse> mockResponseBytes = mock(ResponseBytes.class);
        given(mockResponseBytes.asByteArray()).willReturn(pdfBytes);
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).willReturn(mockResponseBytes);

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        Map<String, Object> mockShipment = new HashMap<>();
        mockShipment.put("id", shipmentId.toString());
        mockShipment.put("status", "DELIVERED");
        given(restTemplate.getForObject(anyString(), eq(Map.class))).willReturn(mockShipment);

        Map<String, Object> event = new HashMap<>();
        event.put("uploadId", uploadId.toString());
        event.put("tenantId", tenantId.toString());
        event.put("objectKey", "uploads/invoice.pdf");
        event.put("documentType", "INVOICE");
        String message = objectMapper.writeValueAsString(event);

        consumer.consume(message);

        ArgumentCaptor<DocumentAuditResult> resultCaptor = ArgumentCaptor.forClass(DocumentAuditResult.class);
        verify(auditRepository).save(resultCaptor.capture());

        DocumentAuditResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getAuditStatus()).isEqualTo("COMPLIANT");
        assertThat(savedResult.getExtractedText()).contains("INV-1002");
        assertThat(savedResult.getExtractedText()).contains(shipmentId.toString());

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void consume_whenWordDocument_thenParsesSuccessfullyAndSavesToS3() throws Exception {
        String invoiceText = "Invoice No: INV-2004\nTotal Amount: Rp 500.000\nShipment ID: " + shipmentId;
        byte[] docxBytes = createMockWord(invoiceText);

        ResponseBytes<GetObjectResponse> mockResponseBytes = mock(ResponseBytes.class);
        given(mockResponseBytes.asByteArray()).willReturn(docxBytes);
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).willReturn(mockResponseBytes);

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        Map<String, Object> mockShipment = new HashMap<>();
        mockShipment.put("id", shipmentId.toString());
        mockShipment.put("status", "IN_TRANSIT");
        given(restTemplate.getForObject(anyString(), eq(Map.class))).willReturn(mockShipment);

        Map<String, Object> event = new HashMap<>();
        event.put("uploadId", uploadId.toString());
        event.put("tenantId", tenantId.toString());
        event.put("objectKey", "uploads/invoice.docx");
        event.put("documentType", "INVOICE");
        String message = objectMapper.writeValueAsString(event);

        consumer.consume(message);

        ArgumentCaptor<DocumentAuditResult> resultCaptor = ArgumentCaptor.forClass(DocumentAuditResult.class);
        verify(auditRepository).save(resultCaptor.capture());

        DocumentAuditResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getAuditStatus()).isEqualTo("COMPLIANT");
        assertThat(savedResult.getExtractedText()).contains("INV-2004");
        assertThat(savedResult.getExtractedText()).contains(shipmentId.toString());

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private byte[] createMockPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                content.setFont(font, 12);
                content.newLineAtOffset(100, 700);
                content.showText(text.replace("\n", " "));
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createMockWord(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }
}
