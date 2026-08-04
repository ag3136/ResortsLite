package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Fixed: cz-java-0069 - Spring Session configuration for Redis-backed sessions
 * 
 * This configuration enables externalized session storage using Redis (Google Cloud Memorystore)
 * to support horizontal scaling and container restarts in GKE Autopilot.
 * 
 * Benefits:
 * - Sessions persist across container restarts
 * - Sessions are shared across all instances in the cluster
 * - No session affinity (sticky sessions) required at load balancer
 * - Supports auto-scaling without session loss
 * 
 * GKE Deployment with Workload Identity Federation:
 * ================================================
 * 1. Create Google Cloud Memorystore for Redis instance in the same VPC as GKE cluster
 * 2. Store Redis credentials in Google Secret Manager:
 *    - REDIS_HOST: Redis instance IP address
 *    - REDIS_PORT: Redis port (default: 6379)
 *    - REDIS_PASSWORD: Redis AUTH password (if enabled)
 * 
 * 3. Configure Workload Identity for the Kubernetes service account:
 *    gcloud iam service-accounts create resortslite-sa
 *    gcloud projects add-iam-policy-binding PROJECT_ID \
 *      --member="serviceAccount:resortslite-sa@PROJECT_ID.iam.gserviceaccount.com" \
 *      --role="roles/secretmanager.secretAccessor"
 * 
 * 4. Bind Kubernetes service account to Google service account:
 *    kubectl annotate serviceaccount resortslite-ksa \
 *      iam.gke.io/gcp-service-account=resortslite-sa@PROJECT_ID.iam.gserviceaccount.com
 * 
 * 5. Mount secrets as environment variables in Kubernetes deployment:
 *    apiVersion: v1
 *    kind: Secret
 *    metadata:
 *      name: redis-credentials
 *    type: Opaque
 *    data:
 *      REDIS_HOST: <base64-encoded-host>
 *      REDIS_PORT: <base64-encoded-port>
 *      REDIS_PASSWORD: <base64-encoded-password>
 * 
 * 6. Reference secrets in deployment:
 *    env:
 *    - name: REDIS_HOST
 *      valueFrom:
 *        secretKeyRef:
 *          name: redis-credentials
 *          key: REDIS_HOST
 *    - name: REDIS_PORT
 *      valueFrom:
 *        secretKeyRef:
 *          name: redis-credentials
 *          key: REDIS_PORT
 *    - name: REDIS_PASSWORD
 *      valueFrom:
 *        secretKeyRef:
 *          name: redis-credentials
 *          key: REDIS_PASSWORD
 * 
 * Redis connection details are configured via environment variables:
 * - REDIS_HOST: Redis server hostname/IP (default: localhost)
 * - REDIS_PORT: Redis server port (default: 6379)
 * - REDIS_PASSWORD: Redis authentication password (optional)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes session timeout
public class SessionConfig {
    
    @Value("${spring.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.redis.password:}")
    private String redisPassword;
    
    /**
     * Fixed: cz-java-0069 - Redis connection factory for externalized session storage
     * 
     * Configures Redis connection using environment variables for GKE deployment.
     * This ensures sessions are stored in Google Cloud Memorystore for Redis,
     * enabling stateless application design and horizontal scaling.
     * 
     * @return RedisConnectionFactory configured with externalized credentials
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        
        // Set password only if provided (some Redis instances don't require AUTH)
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        
        return new LettuceConnectionFactory(redisConfig);
    }
}
