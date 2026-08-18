package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0067: Replaced static in-memory cache with Redis-backed distributed cache
    // Redis cache is managed by Spring Cache abstraction and backed by GCP Memorystore
    // Benefits:
    // - TTL configured (30 minutes) prevents indefinite memory growth
    // - Cache is shared across all application instances (horizontal scaling support)
    // - No cache synchronization issues in multi-instance deployments
    // - Automatic eviction prevents out-of-memory errors
    @Autowired
    private CacheManager cacheManager;

    // FIXED cr-java-0071: Externalized inventory service URL using environment variable
    // This allows different URLs for dev, staging, and production without code changes
    @Value("${app.inventory.endpoint:https://inventory-service.internal:8081/rooms}")
    private String inventoryServiceUrl;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session now backed by GCP Memorystore for Redis
        // Spring Session automatically stores session data in Redis, enabling stateless
        // architecture. Session data is shared across all instances, supporting horizontal
        // scaling, auto-scaling, and failover without data loss.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cr-java-0067: Store booking in Redis-backed cache with TTL
        // Cache is distributed across all instances and automatically expires after 30 minutes
        Cache bookingCache = cacheManager.getCache("bookings");
        if (bookingCache != null) {
            bookingCache.put((String) booking.get("bookingId"), booking);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session data retrieved from Redis-backed session store
        // Data is accessible from any instance in the cluster, ensuring consistent
        // user experience across load-balanced requests.
        String lastGuest = (String) session.getAttribute("guestName");

        // FIXED cr-java-0067: Retrieve booking from Redis-backed cache
        // Cache lookup is consistent across all application instances
        Cache bookingCache = cacheManager.getCache("bookings");
        Map<String, Object> cachedBooking = null;
        if (bookingCache != null) {
            Cache.ValueWrapper wrapper = bookingCache.get(bookingId);
            if (wrapper != null) {
                cachedBooking = (Map<String, Object>) wrapper.get();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("cachedBooking", cachedBooking);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Using externalized configuration from environment variable
        // URL is now configurable via app.inventory.endpoint property or environment variable
        String inventoryUrl = inventoryServiceUrl + "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // VIOLATION czr-java-001 [Software Portability / Mandatory]: Hardcoded absolute
        // file path. This path does not exist inside a container image. Container images
        // have their own isolated file systems — /var/legacy/reports won't be present.
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; // czr-java-001

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
