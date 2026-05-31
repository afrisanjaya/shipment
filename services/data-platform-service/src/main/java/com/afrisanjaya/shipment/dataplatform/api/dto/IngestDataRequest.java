package com.afrisanjaya.shipment.dataplatform.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record IngestDataRequest(
    String dataType,

    @NotEmpty(message = "Payload cannot be empty")
    Map<String, Object> payload
) {}
