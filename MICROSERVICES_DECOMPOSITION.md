# Microservices Decomposition - cz-java-0082 Fix

## Overview
This document describes the fixes applied for containerization blocker **cz-java-0082 (Individual Components)**, which identifies tightly-coupled components that reduce effectiveness in containerized microservices architectures.

## Problem Statement
The original monolithic application had tight coupling between booking and inventory components:
- `BookingController.checkAvailability()` directly called `BookingService.isRoomAvailable()`
- Room availability logic was embedded in the booking service
- No separation of concerns between booking and inventory domains
- Prevented independent deployment and scaling of services

## Solution Applied

### 1. Decoupled Components
**BookingController.java (Line 84)**
- **Before**: Direct method call to `bookingService.isRoomAvailable(roomType)`
- **After**: REST API call to external inventory microservice via `RestTemplate`
- **Benefit**: Enables independent deployment of inventory service as separate GKE pod

**BookingService.java (Line 102)**
- **Before**: `isRoomAvailable()` method contained inventory business logic
- **After**: Method marked as `@Deprecated` and serves only as fallback
- **Benefit**: Inventory logic can be moved to separate service without breaking existing code

### 2. New Components Created

#### RestClientConfig.java
- Provides `RestTemplate` bean for inter-service communication
- Enables HTTP-based microservices communication
- Supports service discovery in Kubernetes environments

#### InventoryController.java
- Demonstrates the inventory microservice pattern
- Provides `/api/inventory/check` endpoint for availability checks
- In production, this should be extracted to a separate project/repository

### 3. Configuration Changes

#### application.properties
```properties
# Externalized inventory service endpoint
app.inventory.endpoint=${INVENTORY_SERVICE_URL:http://localhost:8080}
```

#### Environment Variables
- `INVENTORY_SERVICE_URL`: Points to inventory service endpoint
- In GKE: Use Kubernetes service discovery (e.g., `http://inventory-service:8081`)

## GKE Autopilot Deployment Strategy

### Booking Service Pod
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: booking
        image: gcr.io/project/booking-service:latest
        env:
        - name: INVENTORY_SERVICE_URL
          value: "http://inventory-service:8081"
        - name: REDIS_HOST
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: host
```

### Inventory Service Pod (Separate Deployment)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inventory-service
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: inventory
        image: gcr.io/project/inventory-service:latest
        env:
        - name: INVENTORY_DB_HOST
          valueFrom:
            secretKeyRef:
              name: inventory-db-credentials
              key: host
```

### Kubernetes Service for Service Discovery
```yaml
apiVersion: v1
kind: Service
metadata:
  name: inventory-service
spec:
  selector:
    app: inventory
  ports:
  - port: 8081
    targetPort: 8080
```

## Benefits of This Architecture

### 1. Independent Deployment
- Booking and inventory services can be deployed separately
- Different release cycles for each service
- Reduced deployment risk (smaller blast radius)

### 2. Independent Scaling
- Scale booking service based on booking load
- Scale inventory service based on availability query load
- GKE Autopilot automatically provisions nodes per workload

### 3. Technology Flexibility
- Each service can use different technology stacks
- Inventory service could use different database (e.g., Cloud Spanner)
- Different caching strategies per service

### 4. Security Isolation
- Separate GCP Workload Identity per service
- Different Secret Manager bindings
- Principle of least privilege per service

### 5. Fault Isolation
- Inventory service failure doesn't crash booking service
- Fallback mechanism in place (local availability check)
- Circuit breaker pattern can be added

## Migration Path

### Phase 1: Current State (Completed)
✅ Decoupled controller and service layers
✅ Added REST client for inter-service communication
✅ Externalized service endpoints to environment variables
✅ Created inventory controller as separate endpoint

### Phase 2: Extract Inventory Service (Future)
- Move `InventoryController` to separate project
- Create separate container image for inventory service
- Deploy as separate GKE deployment
- Configure Kubernetes service discovery

### Phase 3: Add Resilience Patterns (Future)
- Implement circuit breaker (e.g., Resilience4j)
- Add retry logic with exponential backoff
- Implement request timeout handling
- Add distributed tracing (e.g., Cloud Trace)

## Testing the Fix

### Local Testing (Monolithic Mode)
```bash
# Start the application
mvn spring-boot:run

# Test availability check (uses local fallback)
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

### Microservices Mode Testing
```bash
# Set inventory service URL
export INVENTORY_SERVICE_URL=http://localhost:8080

# Start the application
mvn spring-boot:run

# Test availability check (calls inventory endpoint)
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"

# Verify inventory endpoint directly
curl "http://localhost:8080/api/inventory/check?roomType=DELUXE"
```

### GKE Testing
```bash
# Deploy to GKE
kubectl apply -f k8s/booking-deployment.yaml
kubectl apply -f k8s/inventory-deployment.yaml
kubectl apply -f k8s/inventory-service.yaml

# Test via ingress
curl "https://booking.example.com/api/bookings/availability?roomType=DELUXE"
```

## Health Check Endpoint

The application already has Spring Boot Actuator configured for health checks:

### Endpoints Available
- `/actuator/health` - Overall application health
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe

### Kubernetes Probe Configuration
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

## Files Modified

1. **BookingController.java** - Decoupled availability check (Line 84)
2. **BookingService.java** - Deprecated local availability method (Line 102)
3. **RestClientConfig.java** - NEW: REST client configuration
4. **InventoryController.java** - NEW: Inventory microservice endpoint
5. **application.properties** - Added inventory service URL configuration
6. **pom.xml** - Fixed malformed dependencies

## Compliance

✅ **cz-java-0082 Fixed**: Tightly-coupled components decomposed for microservices
✅ **GKE Autopilot Ready**: Services can be deployed as independent pods
✅ **Workload Identity Compatible**: Each service can have separate identity
✅ **Secret Manager Ready**: Externalized configuration via environment variables
✅ **Health Check Enabled**: Spring Boot Actuator endpoints configured
✅ **Horizontal Scaling Ready**: Stateless services with external session storage

## Notes

- The inventory service is currently in the same codebase for demonstration
- In production, extract inventory service to separate repository
- Consider adding API gateway (e.g., Cloud Endpoints) for routing
- Implement distributed tracing for debugging across services
- Add service mesh (e.g., Istio) for advanced traffic management
