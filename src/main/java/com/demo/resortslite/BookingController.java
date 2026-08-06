package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cz-java-0082: Decoupled report generation into independent ReportService microservice
    @Autowired
    private ReportService reportService;

    // FIXED cz-java-0070: Migrated from local HashMap cache to Amazon ElastiCache (Redis)
    // Enables horizontal scaling across EKS pods with shared distributed cache
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${REPORT_BASE_PATH:/var/reports}")
    private String reportBasePath;

    // Cache key prefix for Redis
    private static final String BOOKING_CACHE_PREFIX = "booking:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cz-java-0069 (Lines 34-35): In-Memory Session Storage - Removed HttpSession usage
        // Session state now externalized to Amazon ElastiCache (Redis) via Spring Session (see SessionConfig.java).
        // Session data is automatically distributed across all EKS pods for horizontal scaling.
        // No manual session.setAttribute() needed - Spring Session handles persistence transparently.

        // FIXED cz-java-0070 (Line 19): Replaced local HashMap cache with Amazon ElastiCache (Redis)
        // Cache is now shared across all EKS pods via distributed Redis backend
        // Connection details injected via Kubernetes ConfigMaps/Secrets (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)
        redisTemplate.opsForValue().set(BOOKING_CACHE_PREFIX + booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId) {

        // FIXED cz-java-0069: In-Memory Session Storage - Removed HttpSession usage
        // Session state now externalized to Redis. Business state retrieved from database.
        // Spring Session handles session persistence transparently when HttpSession is used.

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP call to
        // internal inventory service. AWS ALB, WAF, and Well-Architected security review
        // enforce HTTPS. This call will be blocked or flagged in a cloud-native setup.
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available"; // cr-java-0088

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // FIXED cz-java-0082 (Line 84): Decomposed tightly-coupled component into independent microservice
        // Report generation now delegated to dedicated ReportService, enabling independent deployment
        // as a separate EKS microservice with its own Deployment, Service, and ConfigMap.
        // This follows microservices best practices: single responsibility and loose coupling.
        String year = "2024"; // Could be parameterized
        Map<String, Object> reportResult = reportService.generateMonthlyReport(month, year);

        Map<String, Object> response = new HashMap<>();
        response.put("status", reportResult.get("status"));
        response.put("reportPath", reportResult.get("path"));
        return response;
    }
}
