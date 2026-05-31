package com.afrisanjaya.shipment.dataplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record UploadStorageProperties(
        String storageDir
) {
}
