package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS configuration beans.
 *
 * blocker-2/3 (cz-java-0057): Provides an S3Client bean used by ReportService to
 * read/write files to Amazon S3 instead of hardcoded absolute local filesystem paths.
 * The AWS region is sourced from the AWS_REGION environment variable (defaulting to
 * us-east-1), and credentials are resolved automatically via the AWS Default Credential
 * Provider Chain (IAM role, environment variables, or instance profile).
 */
@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client() {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
