package com.afrisanjaya.shipment.dataplatform.api.dto;

import java.util.Map;

public record DataQueryResponse(
    Map<String, Object> result
) {}
