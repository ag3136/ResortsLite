# HTTP Session State Storage Migration to Amazon ElastiCache for Redis

## Rule: cr-java-0065 - HTTP Session State Storage

### Problem Statement
The application was storing critical application state and user data in HTTP session objects, creating server affinity and preventing horizontal scaling. Session-based state storage violated cloud-native stateless principles and caused data loss when instances were terminated or load balanced across multiple servers.

### Solution Implemented
Migrated all HTTP session state to Amazon ElastiCache for Redis using Spring Session, enabling stateless application instances with centralized, distributed session management.

## Changes Made

### 1. Maven Dependencies (pom.xml)
Added three new dependencies for Spring Session with Redis:

```xml
<!-- Spring Session Data Redis - Distributed session management with ElastiCache -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>

<!-- Lettuce - Redis client for Spring Session -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>

<!-- Spring Data Redis - Redis integration -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 2. Redis Configuration Class (RedisSessionConfig.java)
Created a new configuration class to enable Spring Session with Redis:

**Location:** `src/main/java/com/demo/resortslite/config/RedisSessionConfig.java`

**Key Features:**
- Enables Redis-backed HTTP sessions with `@EnableRedisHttpSession`
- Configures session timeout to 30 minutes (1800 seconds)
- Uses Lettuce connection factory for Redis connectivity
- Automatically integrates with Spring Session infrastructure

### 3. Application Properties (application.properties)
Added Redis/ElastiCache configuration:

```properties
# Spring Session with Redis (Amazon ElastiCache) - Distributed session management
spring.session.store-type=redis
spring.session.redis.namespace=spring:session
spring.session.timeout=1800s

# Redis/ElastiCache Configuration
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
```

**Configuration Details:**
- `spring.session.store-type=redis`: Enables Redis as the session store
- `spring.session.redis.namespace=spring:session`: Sets Redis key namespace
- `spring.session.timeout=1800s`: 30-minute session timeout
- `REDIS_HOST` and `REDIS_PORT`: Environment variables for cloud deployment

### 4. BookingController.java Updates
Updated the controller to document the Spring Session integration:

**Fixed Occurrences:**
1. **Line 6**: Import statement - Added documentation about Redis backing
2. **Line 27**: `session.setAttribute("lastBooking", booking)` - Now writes to Redis
3. **Line 34-35**: `session.setAttribute("guestName", guestName)` - Now writes to Redis
4. **Line 48**: `session.getAttribute("guestName")` - Now reads from Redis

**Key Points:**
- No code changes required to HttpSession usage
- Spring Session automatically intercepts all session operations
- All `setAttribute()` calls now write to Redis
- All `getAttribute()` calls now read from Redis
- Session data is automatically serialized/deserialized

## How It Works

### Spring Session Architecture
1. **Transparent Interception**: Spring Session uses a servlet filter to intercept all HttpSession operations
2. **Redis Storage**: Session data is stored in Redis with automatic serialization
3. **Session ID Cookie**: Client receives a session cookie (SESSION) that maps to Redis keys
4. **Cross-Instance Access**: All application instances share the same Redis backend
5. **Automatic Cleanup**: Redis TTL ensures expired sessions are automatically removed

### Redis Key Structure
```
spring:session:sessions:<session-id>
spring:session:sessions:expires:<session-id>
spring:session:expirations:<timestamp>
```

### Benefits Achieved
✅ **Stateless Instances**: Application instances no longer store session data locally
✅ **Horizontal Scaling**: New instances can be added/removed without session loss
✅ **Load Balancing**: Requests can be routed to any instance without sticky sessions
✅ **High Availability**: ElastiCache replication provides session data redundancy
✅ **Auto-Scaling Compatible**: Instances can be terminated without losing user sessions
✅ **Cloud-Native**: Follows 12-factor app principles for stateless processes

## Deployment Requirements

### AWS ElastiCache Setup
1. Create an ElastiCache for Redis cluster in your VPC
2. Configure security groups to allow access from application instances
3. Note the Redis endpoint hostname and port

### Environment Variables
Set these environment variables in your ECS/EKS deployment:

```bash
REDIS_HOST=your-elasticache-cluster.cache.amazonaws.com
REDIS_PORT=6379
```

### Local Development
For local testing, run Redis in Docker:

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

The application will use `localhost:6379` by default.

## Testing the Fix

### Verify Session Distribution
1. Start multiple application instances
2. Create a booking (POST /api/bookings/create)
3. Note the session cookie in the response
4. Query booking status (GET /api/bookings/status/{bookingId}) from a different instance
5. Verify that session data (guestName) is correctly retrieved

### Verify Redis Storage
Connect to Redis and inspect session data:

```bash
redis-cli -h your-elasticache-cluster.cache.amazonaws.com
KEYS spring:session:*
GET spring:session:sessions:<session-id>
```

## Migration Impact

### Code Changes
- **Minimal**: No changes to business logic or HttpSession API usage
- **Additive**: Only added configuration and dependencies
- **Backward Compatible**: Works with existing session-based code

### Performance Considerations
- **Network Latency**: Session operations now involve Redis network calls
- **Serialization Overhead**: Session attributes are serialized to Redis
- **Mitigation**: Use ElastiCache in the same VPC/AZ for low latency

### Operational Changes
- **New Dependency**: Application now requires Redis/ElastiCache
- **Monitoring**: Monitor Redis connection pool and memory usage
- **Backup**: ElastiCache provides automatic backups and snapshots

## Violations Fixed

| Line | Original Issue | Fix Applied |
|------|---------------|-------------|
| 6 | HttpSession import without distributed backing | Now backed by Redis via Spring Session |
| 27 | session.setAttribute("lastBooking", booking) | Writes to Redis automatically |
| 34 | session.setAttribute("guestName", guestName) | Writes to Redis automatically |
| 35 | session.setAttribute("guestName", guestName) | Writes to Redis automatically |
| 48 | session.getAttribute("guestName") | Reads from Redis automatically |

All 5 occurrences of cr-java-0065 have been successfully resolved.
