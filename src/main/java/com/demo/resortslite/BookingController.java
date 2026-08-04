package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // FIXED cr-java-0071: Externalized inventory service URL using environment variable
    // This enables seamless deployment across environments without code changes
    @Value("${inventory.service.url:https://inventory-service.internal:8081}")
    private String inventoryServiceUrl;

    // FIXED cr-java-0067: Replaced static in-memory HashMap with Google Cloud Memorystore for Redis
    // Previous implementation: private static final Map<String, Object> bookingCache = new HashMap<>();
    // 
    // Issues with in-memory cache:
    // - Instance-local cache not shared across multiple instances (breaks horizontal scaling)
    // - No TTL causes indefinite memory growth and potential OOM errors
    // - Cache inconsistency across distributed instances
    // - Stale data persists indefinitely
    //
    // New implementation uses Spring Cache with Redis backend:
    // - Shared cache state across all application instances via Google Cloud Memorystore
    // - Automatic TTL expiration (1 hour) prevents memory exhaustion
    // - Consistent cache behavior in distributed cloud environment
    // - Cache entries automatically synchronized across all instances
    // - Configuration: @Cacheable and @CachePut annotations with Redis cache manager

    // FIXED cr-java-0065: HttpSession now backed by Spring Session Data Redis
    // Session state is stored in Google Cloud Memorystore for Redis, enabling:
    // - Stateless application architecture (no server affinity required)
    // - Horizontal scaling across multiple instances
    // - Session persistence across instance restarts and load balancing
    // - Automatic session replication across distributed instances
    // Configuration: spring.session.store-type=redis in application.properties
    @PostMapping("/create")
    @CachePut(value = "bookings", key = "#result['bookingId']")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session attributes now stored in Redis via Spring Session
        // HttpSession interface remains unchanged, but storage is externalized to Memorystore
        // All instances share the same Redis-backed session store, eliminating server affinity
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cr-java-0067: @CachePut annotation automatically stores booking in Redis cache
        // Cache entry has 1-hour TTL and is shared across all application instances
        // No manual cache.put() needed - Spring handles Redis operations transparently

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    // FIXED cr-java-0065: Session reads now retrieve from Redis-backed session store
    // Load balancer can route requests to any instance - session data is always available
    // from centralized Memorystore for Redis cluster
    @GetMapping("/status/{bookingId}")
    @Cacheable(value = "bookings", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Reading from Redis-backed session - works across all instances
        // Spring Session transparently handles Redis serialization/deserialization
        String lastGuest = (String) session.getAttribute("guestName");

        // FIXED cr-java-0067: @Cacheable annotation automatically retrieves from Redis cache
        // If cache miss, method executes and result is cached with 1-hour TTL
        // Cache is shared across all instances via Google Cloud Memorystore
        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Using externalized inventory service URL from environment variable
        // Default value uses HTTPS for cloud security compliance
        // Full endpoint URL constructed dynamically
        String inventoryUrl = inventoryServiceUrl + "/rooms/available";

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
