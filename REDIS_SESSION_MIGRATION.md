# Redis Session Configuration for AWS ElastiCache

## Overview

This application has been migrated from in-memory HTTP sessions to distributed session management using **Spring Session Data Redis** backed by **Amazon ElastiCache for Redis**.

## What Was Fixed

**Rule ID**: cr-java-0065  
**Rule Name**: HTTP Session State Storage  
**Severity**: HIGH  
**Category**: State Management & Session Issues

### Problem
The application previously stored critical application state and user data in HTTP session objects, which:
- Created server affinity and prevented horizontal scaling
- Violated cloud-native stateless principles
- Caused data loss when instances were terminated or load balanced across multiple servers
- Made sessions instance-local and invisible to other EC2 instances

### Solution
Migrated all HTTP session state to Amazon ElastiCache for Redis using Spring Session Data Redis, enabling:
- **Stateless application instances**: Sessions stored externally in Redis
- **Horizontal scaling**: Multiple EC2 instances share session data
- **High availability**: ElastiCache provides automatic failover and replication
- **Session persistence**: Sessions survive application restarts and deployments
- **Load balancer compatibility**: AWS ALB can distribute requests without sticky sessions

## Files Modified

### 1. pom.xml
Added Spring Session Data Redis dependencies:
```xml
<!-- Spring Session Data Redis for distributed session management -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
<!-- Lettuce Redis client (default for Spring Boot) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<!-- Redis connection pooling -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

### 2. application.properties
Added Redis and Spring Session configuration:
```properties
# Spring Session Configuration - Redis backed distributed sessions
spring.session.store-type=redis
spring.session.redis.flush-mode=on_save
spring.session.redis.namespace=spring:session
spring.session.timeout=1800s

# Redis Configuration for Amazon ElastiCache
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD:}
spring.redis.ssl=${REDIS_SSL:false}
spring.redis.timeout=2000ms

# Redis connection pool configuration
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.min-idle=2
spring.redis.lettuce.pool.max-wait=-1ms
```

### 3. RedisSessionConfig.java (NEW)
Created Spring Session configuration class:
- Enables Redis HTTP session with `@EnableRedisHttpSession`
- Configures RedisTemplate with JSON serialization for complex objects
- Sets session timeout to 30 minutes (1800 seconds)

### 4. BookingController.java
Updated to document Redis-backed session usage:
- Line 6: HttpSession import (unchanged - Spring Session intercepts transparently)
- Line 27: HttpSession parameter in createBooking() - now backed by Redis
- Line 34-35: session.setAttribute() calls - now persist to Redis
- Line 48: HttpSession parameter in getBookingStatus() - now backed by Redis
- Line 48: session.getAttribute() call - now reads from Redis

## AWS ElastiCache Setup

### Step 1: Create ElastiCache for Redis Cluster

```bash
# Create a Redis cluster in your VPC
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --cache-subnet-group-name your-subnet-group \
  --security-group-ids sg-xxxxxxxxx \
  --preferred-availability-zone us-east-1a
```

### Step 2: Configure Security Groups

Allow inbound traffic on port 6379 from your application security group:
```bash
aws ec2 authorize-security-group-ingress \
  --group-id sg-redis-xxxxxxxxx \
  --protocol tcp \
  --port 6379 \
  --source-group sg-app-xxxxxxxxx
```

### Step 3: Get ElastiCache Endpoint

```bash
aws elasticache describe-cache-clusters \
  --cache-cluster-id resorts-lite-redis \
  --show-cache-node-info \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint.Address' \
  --output text
```

### Step 4: Configure Environment Variables

Set the following environment variables in your deployment:

**For ECS Task Definition:**
```json
{
  "environment": [
    {
      "name": "REDIS_HOST",
      "value": "resorts-lite-redis.abc123.0001.use1.cache.amazonaws.com"
    },
    {
      "name": "REDIS_PORT",
      "value": "6379"
    },
    {
      "name": "REDIS_SSL",
      "value": "true"
    }
  ]
}
```

**For Elastic Beanstalk:**
```bash
aws elasticbeanstalk update-environment \
  --environment-name resorts-lite-prod \
  --option-settings \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=REDIS_HOST,Value=resorts-lite-redis.abc123.0001.use1.cache.amazonaws.com \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=REDIS_PORT,Value=6379 \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=REDIS_SSL,Value=true
```

**For EKS (Kubernetes):**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resorts-lite-config
data:
  REDIS_HOST: "resorts-lite-redis.abc123.0001.use1.cache.amazonaws.com"
  REDIS_PORT: "6379"
  REDIS_SSL: "true"
```

## Production Recommendations

### 1. Enable Encryption in Transit
```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-redis \
  --transit-encryption-enabled \
  --auth-token "your-strong-auth-token"
```

Set environment variables:
```
REDIS_SSL=true
REDIS_PASSWORD=your-strong-auth-token
```

### 2. Enable Encryption at Rest
```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-redis \
  --at-rest-encryption-enabled
```

### 3. Use Redis Cluster Mode for High Availability
```bash
aws elasticache create-replication-group \
  --replication-group-id resorts-lite-redis-cluster \
  --replication-group-description "Redis cluster for session storage" \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-clusters 3 \
  --automatic-failover-enabled \
  --multi-az-enabled
```

### 4. Configure Backup and Restore
```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-redis \
  --snapshot-retention-limit 7 \
  --snapshot-window "03:00-05:00"
```

### 5. Monitor Redis Metrics
Key CloudWatch metrics to monitor:
- `CPUUtilization`: Should stay below 75%
- `DatabaseMemoryUsagePercentage`: Should stay below 80%
- `CurrConnections`: Monitor connection pool usage
- `Evictions`: Should be 0 (increase memory if evictions occur)
- `NetworkBytesIn/Out`: Monitor network throughput

## Testing

### Local Development
For local development, run Redis using Docker:
```bash
docker run -d -p 6379:6379 --name redis redis:7-alpine
```

The application will connect to `localhost:6379` by default.

### Verify Session Storage
1. Create a booking:
```bash
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=DELUXE&checkIn=2024-01-01&checkOut=2024-01-05" \
  -c cookies.txt
```

2. Check session in Redis:
```bash
redis-cli
> KEYS spring:session:*
> GET spring:session:sessions:<session-id>
```

3. Verify session persistence across requests:
```bash
curl -X GET "http://localhost:8080/api/bookings/status/BK-12345678" \
  -b cookies.txt
```

## Session Data Structure

Sessions are stored in Redis with the following structure:
```
Key: spring:session:sessions:<session-id>
Value: {
  "lastBooking": {
    "bookingId": "BK-12345678",
    "guestName": "John Doe",
    "roomType": "DELUXE",
    "checkIn": "2024-01-01",
    "checkOut": "2024-01-05"
  },
  "guestName": "John Doe"
}
```

Session keys have a TTL of 1800 seconds (30 minutes) and are automatically cleaned up by Redis.

## Troubleshooting

### Connection Issues
If the application cannot connect to Redis:
1. Verify security group rules allow traffic on port 6379
2. Check VPC routing and subnet configuration
3. Verify ElastiCache endpoint is correct
4. Check application logs for connection errors

### Session Not Persisting
If sessions are not persisting across instances:
1. Verify `spring.session.store-type=redis` is set
2. Check Redis connection is successful in application logs
3. Verify session cookies are being sent by the client
4. Check Redis memory usage (sessions may be evicted if memory is full)

### Performance Issues
If Redis is slow:
1. Check ElastiCache CPU and memory metrics
2. Increase cache node size if needed
3. Enable Redis cluster mode for better performance
4. Review connection pool settings

## Benefits Achieved

✅ **Stateless Application**: Instances can be added/removed without session loss  
✅ **Horizontal Scaling**: Multiple EC2 instances share session data seamlessly  
✅ **High Availability**: ElastiCache provides automatic failover  
✅ **Session Persistence**: Sessions survive deployments and restarts  
✅ **Load Balancer Compatible**: No need for sticky sessions  
✅ **Cloud-Native**: Follows 12-factor app principles  
✅ **AWS Optimized**: Leverages managed ElastiCache service  

## Cost Considerations

- **ElastiCache for Redis**: ~$15-50/month for cache.t3.micro (varies by region)
- **Data Transfer**: Minimal cost for session data (typically <1GB/month)
- **Backup Storage**: $0.085/GB-month for automated backups

For production workloads, consider:
- cache.t3.small or larger for better performance
- Multi-AZ deployment for high availability
- Reserved instances for cost savings (up to 55% discount)

## Next Steps

1. ✅ Deploy ElastiCache for Redis in your AWS account
2. ✅ Configure security groups and VPC networking
3. ✅ Set REDIS_HOST environment variable in your deployment
4. ✅ Enable encryption in transit and at rest for production
5. ✅ Configure CloudWatch alarms for Redis metrics
6. ✅ Test session persistence across multiple application instances
7. ✅ Monitor session storage usage and adjust Redis memory as needed

## References

- [Spring Session Data Redis Documentation](https://docs.spring.io/spring-session/reference/guides/boot-redis.html)
- [Amazon ElastiCache for Redis Documentation](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/)
- [Spring Boot Redis Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.nosql.redis)
- [AWS ElastiCache Best Practices](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/BestPractices.html)
