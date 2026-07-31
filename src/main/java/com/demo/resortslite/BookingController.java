package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0067: Replaced static in-memory cache with Azure Cache for Redis
    // The static HashMap has been removed and replaced with Spring Cache abstraction
    // backed by Azure Cache for Redis with TTL policies configured in RedisCacheConfig.
    // Benefits:
    // - Distributed caching across all application instances
    // - Automatic TTL expiration (1 hour for booking cache)
    // - No memory exhaustion - Redis manages memory with eviction policies
    // - Cache consistency - all instances share the same cache
    // - Horizontal scaling - no cache synchronization issues

    @PostMapping("/create")
    @CachePut(value = "bookingCache", key = "#result['bookingId']")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session state now externalized to Azure Cache for Redis
        // Spring Session Data Redis automatically stores session data in Redis instead of in-memory.
        // This enables stateless architecture and horizontal scaling across multiple instances.
        // HttpSession interface remains the same, but backend storage is now Redis-based.
        // Configuration: spring.session.store-type=redis in application.properties
        session.setAttribute("lastBooking", booking); // cr-java-0065 FIXED
        session.setAttribute("guestName", guestName); // cr-java-0065 FIXED

        // FIXED cr-java-0067: Cache is now automatically managed by Spring Cache with Redis
        // @CachePut annotation stores the booking in Redis with the bookingId as the key
        // TTL is configured to 1 hour in RedisCacheConfig
        // No manual cache management required - Spring handles it transparently

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookingCache", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Reading session state from Azure Cache for Redis
        // Session data is now shared across all application instances via Redis.
        // No more instance affinity required - any instance can serve any request.
        String lastGuest = (String) session.getAttribute("guestName"); // cr-java-0065 FIXED

        // FIXED cr-java-0067: @Cacheable annotation checks Redis cache first
        // If booking is found in cache, method execution is skipped
        // If not found, method executes and result is stored in Redis with TTL
        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
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
        // VIOLATION czr-java-001 [Cloud Compatibility / Mandatory]: Hardcoded absolute
        // file path. This path does not exist inside a container image. Container images
        // have their own isolated file systems — /var/legacy/reports won't be present.
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; // czr-java-001

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
