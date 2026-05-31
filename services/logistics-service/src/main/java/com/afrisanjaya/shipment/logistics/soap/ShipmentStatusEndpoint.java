package com.afrisanjaya.shipment.logistics.soap;

import com.afrisanjaya.shipment.logistics.domain.entity.Shipment;
import com.afrisanjaya.shipment.logistics.domain.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/ws")
@RequiredArgsConstructor
public class ShipmentStatusEndpoint {

    private final ShipmentRepository shipmentRepository;

    @PostMapping(value = "/shipments",
            consumes = MediaType.TEXT_XML_VALUE,
            produces = MediaType.TEXT_XML_VALUE)
    public String getShipmentStatus(@RequestBody String soapRequest) {
        log.info("[SOAP] Received legacy system request");

        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new ByteArrayInputStream(
                    soapRequest.getBytes(StandardCharsets.UTF_8)));

            var shipmentIdNode = doc.getElementsByTagNameNS("*", "shipmentId").item(0);
            if (shipmentIdNode == null) {
                return soapFault("MISSING_FIELD", "shipmentId is required");
            }

            UUID shipmentId = UUID.fromString(shipmentIdNode.getTextContent().trim());
            Optional<Shipment> found = shipmentRepository.findById(shipmentId);

            if (found.isEmpty()) {
                return soapFault("NOT_FOUND", "Shipment not found: " + shipmentId);
            }

            Shipment s = found.get();
            return buildSoapResponse(s);

        } catch (Exception e) {
            log.error("[SOAP] Error processing request: {}", e.getMessage(), e);
            return soapFault("INTERNAL_ERROR", e.getMessage());
        }
    }

    private String buildSoapResponse(Shipment s) throws Exception {
        var docFactory = DocumentBuilderFactory.newInstance();
        var docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element envelope = doc.createElementNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "soap:Envelope");
        doc.appendChild(envelope);

        Element body = doc.createElementNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "soap:Body");
        envelope.appendChild(body);

        Element response = doc.createElementNS(
                "http://Shipment.com/logistics/ws", "tns:GetShipmentStatusResponse");
        body.appendChild(response);

        addElement(doc, response, "tns:shipmentId", s.getId().toString());
        addElement(doc, response, "tns:status", s.getStatus().name());
        addElement(doc, response, "tns:priority", s.getPriority());
        addElement(doc, response, "tns:quantity", String.valueOf(s.getQuantity()));
        addElement(doc, response, "tns:estimatedDelivery",
                s.getEstimatedDeliveryAt() != null ? s.getEstimatedDeliveryAt().toString() : "N/A");

        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty("omit-xml-declaration", "yes");
        var writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String soapFault(String code, String message) {
        return """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:%s</faultcode>
                  <faultstring>%s</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """.formatted(code, message);
    }

    private void addElement(Document doc, Element parent, String name, String value) {
        Element el = doc.createElementNS("http://Shipment.com/logistics/ws", name);
        el.setTextContent(value);
        parent.appendChild(el);
    }
}
