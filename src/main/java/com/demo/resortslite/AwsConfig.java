package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AwsConfig — wires AWS SDK v2 clients and Redis template as Spring beans.
 *
 * All clients use DefaultCredentialsProvider which resolves credentials from:
 *   1. Environment variables (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY)
 *   2. EC2/ECS instance profile / task role (recommended for cloud deployments)
 *   3. ~/.aws/credentials (local development only)
 *
 * This eliminates any hard-coded AWS credentials in source code.
 */
@Configuration
public class AwsConfig {

    @Value("${cloud.aws.region.static:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    /**
     * Amazon S3 client — used by ReportService to replace local file system operations.
     * Addresses cr-java-0061, cr-java-0062, cr-java-0063.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS Secrets Manager client — used by BookingService to retrieve DB credentials
     * and authentication tokens at runtime.
     * Addresses cr-java-0069, cr-java-0090.
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS SSM Parameter Store client — used by ReportService and BookingController
     * to retrieve environment-specific URLs and configuration at runtime.
     * Addresses cr-java-0071, cr-java-0077.
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * RedisTemplate configured for Amazon ElastiCache — used by BookingController
     * to replace the unbounded in-memory HashMap cache with TTL-aware distributed cache.
     * Addresses cr-java-0067.
     *
     * Also supports Spring Session backed by Redis for distributed session management.
     * Addresses cr-java-0065.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
