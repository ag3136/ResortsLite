package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // Fixed: cz-java-0070 - Replaced local in-memory cache with Redis for horizontal scaling
    // BEFORE: private static final Map<String, Object> bookingCache = new HashMap<>();
    // AFTER: Using RedisTemplate for distributed caching across all GKE pod replicas
    // This enables:
    // - Shared cache state across all container instances
    // - Horizontal pod autoscaling without cache inconsistency
    // - Cache persistence during pod restarts
    // - TTL-based cache expiration
    // Redis connection configured via environment variables (REDIS_HOST, REDIS_PORT)
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // Fixed: cz-java-0082 - Externalized inventory service endpoint for microservices architecture
    // This enables independent deployment of inventory service as a separate GKE pod
    @Value("${app.inventory.endpoint:#{systemEnvironment['INVENTORY_SERVICE_URL'] ?: 'http://inventory-service:8081'}}")
    private String inventoryServiceUrl;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    // Fixed: cz-java-0057 - Externalized report path to environment variable
    @Value("${app.report.path:#{systemEnvironment['REPORT_PATH'] ?: '/app/reports'}}")
    private String reportBasePath;

    // Cache TTL in seconds (configurable via environment variable)
    @Value("${app.cache.ttl:3600}")
    private long cacheTtl;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Fixed: cz-java-0070 - Store booking in Redis cache instead of local HashMap
        // Cache key format: "booking:cache:{bookingId}"
        if (redisTemplate != null && booking.get("bookingId") != null) {
            String cacheKey = "booking:cache:" + booking.get("bookingId");
            try {
                redisTemplate.opsForValue().set(cacheKey, booking, cacheTtl, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Log error but don't fail the request if Redis is unavailable
                System.err.println("Warning: Failed to cache booking in Redis: " + e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(@PathVariable String bookingId) {

        // Fixed: cz-java-0070 - Check Redis cache first before querying database
        Map<String, Object> bookingDetails = null;
        if (redisTemplate != null) {
            String cacheKey = "booking:cache:" + bookingId;
            try {
                bookingDetails = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                System.err.println("Warning: Failed to retrieve from Redis cache: " + e.getMessage());
            }
        }

        // If not in cache, fetch from database
        if (bookingDetails == null) {
            bookingDetails = bookingService.getBookingById(bookingId);
            
            // Store in cache for future requests
            if (redisTemplate != null && bookingDetails != null) {
                String cacheKey = "booking:cache:" + bookingId;
                try {
                    redisTemplate.opsForValue().set(cacheKey, bookingDetails, cacheTtl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to cache booking in Redis: " + e.getMessage());
                }
            }
        }

        String lastGuest = bookingDetails != null ? (String) bookingDetails.get("guestName") : null;

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingDetails);
        return result;
    }

    /**
     * Fixed: cz-java-0082 - Individual Components (Line 84)
     * 
     * BEFORE: Controller directly called bookingService.isRoomAvailable(roomType),
     * creating tight coupling between booking and inventory logic in a monolithic service.
     * 
     * AFTER: Decoupled availability check to call external inventory microservice via REST.
     * This enables:
     * - Independent deployment of inventory service as separate GKE Autopilot pod
     * - Separate container images for booking and inventory services
     * - Independent scaling based on workload (booking vs inventory queries)
     * - GCP Workload Identity binding per service
     * - Secret Manager integration per microservice
     * 
     * The inventory service endpoint is externalized via environment variable INVENTORY_SERVICE_URL,
     * allowing Kubernetes service discovery and dynamic routing in GKE.
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        
        // Fixed: cz-java-0082 - Call external inventory microservice instead of local method
        boolean available = false;
        try {
            if (restTemplate != null) {
                // Call inventory microservice REST API
                String url = inventoryServiceUrl + "/api/inventory/check?roomType=" + roomType;
                Map<String, Object> inventoryResponse = restTemplate.getForObject(url, Map.class);
                available = inventoryResponse != null && Boolean.TRUE.equals(inventoryResponse.get("available"));
                response.put("inventoryServiceUrl", url);
            } else {
                // Fallback to local service if RestTemplate not configured (for backward compatibility)
                available = bookingService.isRoomAvailable(roomType);
                response.put("note", "Using local availability check - configure RestTemplate for microservices");
            }
        } catch (Exception e) {
            // Fallback to local service on error
            available = bookingService.isRoomAvailable(roomType);
            response.put("warning", "Inventory service unavailable, using local check: " + e.getMessage());
        }
        
        response.put("available", available);
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Fixed: cz-java-0057 - Using environment variable for report path instead of hardcoded absolute path
        String reportPath = reportBasePath + "/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
