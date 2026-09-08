package com.demo.resortslite;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
public class AzureBlobStorageService {

    private final String connectionString;
    private final String endpoint;
    private final String containerName;

    public AzureBlobStorageService(
            @Value("${azure.storage.connection-string:}") String connectionString,
            @Value("${azure.storage.endpoint:}") String endpoint,
            @Value("${azure.storage.container-name:reports}") String containerName) {
        this.connectionString = connectionString;
        this.endpoint = endpoint;
        this.containerName = containerName;
    }

    public String uploadText(String blobName, String content) {
        BlobContainerClient containerClient = getContainerClient();
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        BlockBlobClient blobClient = containerClient.getBlobClient(blobName).getBlockBlobClient();
        blobClient.upload(new ByteArrayInputStream(payload), payload.length, true);
        return blobClient.getBlobUrl();
    }

    private BlobContainerClient getContainerClient() {
        BlobServiceClient serviceClient;
        if (connectionString != null && !connectionString.trim().isEmpty()) {
            serviceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        } else if (endpoint != null && !endpoint.trim().isEmpty()) {
            serviceClient = new BlobServiceClientBuilder().endpoint(endpoint).buildClient();
        } else {
            throw new IllegalStateException("Azure Blob Storage configuration is missing");
        }

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        return containerClient;
    }
}
