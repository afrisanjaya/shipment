package com.afrisanjaya.shipment.ai.api.dto;

import java.util.UUID;

public record LogisticsMatch(
    UUID id,
    String name,
    String type,
    String location,
    double score,
    String excerpt
) {}