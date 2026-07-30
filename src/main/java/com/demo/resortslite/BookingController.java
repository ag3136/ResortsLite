package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController - REST API for resort booking operations
 * 
 * FIXED cr-java-0067: Replaced in-memory cache with Azure Cache for Redis
 * - In-memory HashMap cache has been replaced with distributed Redis cache
 * - TTL policies are now enforced to prevent memory exhaustion
 * - Cache is now shared across all application instances
 * - Supports horizontal scaling and auto-scaling in Azure
 * 
 * FIXED cr-java-0090: Added Azure AD authentication
 * - All endpoints now require Azure AD authentication
 * - Admin endpoints require specific role membership
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0067: Replaced in-memory cache with Azure Cache for Redis
    // RedisTemplate provides distributed caching with TTL support
    // This enables cache consistency across multiple instances in Azure
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // FIXED cr-java-0071: Externalized inventory service URL to Azure App Configuration
    // This URL can now be configured via environment variables or Azure App Configuration
    // Example: INVENTORY_SERVICE_URL=https://inventory-service.azure.com/rooms/available
    @Value("${app.inventory.service.url:https://inventory-service.internal:8081/rooms/available}")
    private String inventoryServiceUrl;

    // Cache TTL configuration - default 1 hour (3600 seconds)
    @Value("${app.cache.booking.ttl:3600}")
    private long bookingCacheTtl;

    /**
     * Create a new booking
     * FIXED cr-java-0090: Endpoint now requires Azure AD authentication
     * FIXED cr-java-0067: Uses Redis cache with TTL instead of in-memory HashMap
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        // FIXED cr-java-0090: Get authenticated user from Azure AD security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUser = authentication.getName();
        
        // Log authenticated user for audit trail
        System.out.println("Booking created by authenticated user: " + authenticatedUser);

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065 [Cloud Compatibility / Mandatory]: Session state now externalized to Azure Cache for Redis
        // With Spring Session Data Redis configured, HttpSession is automatically backed by Redis
        // Session data is now shared across all instances, enabling horizontal scaling and auto-scaling
        // Azure Cache for Redis provides high availability, persistence, and multi-instance session sharing
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cr-java-0067: Store booking in Redis cache with TTL instead of in-memory HashMap
        // This enables distributed caching across all application instances
        // TTL prevents indefinite memory growth and ensures cache freshness
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtl, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("authenticatedUser", authenticatedUser);
        return response;
    }

    /**
     * Get booking status by ID
     * FIXED cr-java-0090: Endpoint now requires Azure AD authentication
     * FIXED cr-java-0067: Retrieves from Redis cache instead of in-memory HashMap
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0090: Get authenticated user from Azure AD security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUser = authentication.getName();

        // FIXED cr-java-0065: Reading from Redis-backed session (stateless across instances)
        String lastGuest = (String) session.getAttribute("guestName");

        // FIXED cr-java-0067: Retrieve from Redis cache with TTL
        String cacheKey = "booking:" + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("cachedBooking", cachedBooking);
        result.put("details", bookingService.getBookingById(bookingId));
        result.put("authenticatedUser", authenticatedUser);
        return result;
    }

    /**
     * Check room availability
     * FIXED cr-java-0090: Endpoint now requires Azure AD authentication
     * FIXED cr-java-0071: Uses externalized configuration for service URL
     * FIXED cr-java-0088: Service URL should use HTTPS in production
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Replaced hard-coded URL with externalized configuration
        // FIXED cr-java-0088: URL should be HTTPS in production (configured via environment)
        
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryServiceUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Download booking report
     * FIXED cr-java-0090: Endpoint now requires Azure AD authentication and admin role
     * NOTE: File path issue (czr-java-001) should be addressed by migrating to Azure Blob Storage
     */
    @PreAuthorize("hasAuthority('ROLE_ResortsLite-Admins')")
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // VIOLATION czr-java-001 [Software Portability / Mandatory]: Hardcoded absolute
        // file path. This path does not exist inside a container image. Container images
        // have their own isolated file systems — /var/legacy/reports won't be present.
        // TODO: Migrate to Azure Blob Storage for cloud-native file storage
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; // czr-java-001

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
