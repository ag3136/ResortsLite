package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// FIXED cr-java-0065: HttpSession now backed by Azure Cache for Redis via Spring Session
// Spring Session Data Redis automatically externalizes session state to Redis
// This enables stateless architecture and horizontal scaling across multiple Azure Container Apps instances
// Session data is shared across all instances via Azure Cache for Redis
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0090: Inject Azure AD authentication helper
    @Autowired
    private AzureAdAuthenticationHelper authHelper;

    // FIXED cr-java-0071: Externalized inventory service URL to configuration
    // In production, this should be loaded from Azure App Configuration
    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    // FIXED cr-java-0067: Removed static in-memory cache without TTL
    // Replaced with Spring Cache abstraction backed by Azure Cache for Redis
    // Benefits:
    // - Distributed cache shared across all application instances
    // - TTL policies prevent indefinite memory growth (1 hour default)
    // - Cache consistency across horizontally scaled instances
    // - Automatic eviction of stale data
    // - High availability and persistence via Azure Cache for Redis
    // 
    // The @Cacheable, @CachePut, and @CacheEvict annotations below manage the cache automatically
    // Cache entries are stored in Redis with the cache name "bookings"

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    @CachePut(value = "bookings", key = "#result['bookingId']")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        // FIXED cr-java-0090: Add Azure AD user context to response
        // User authentication is now handled by Azure AD instead of file-based credentials
        String authenticatedUser = authHelper.getCurrentUserEmail();
        String userId = authHelper.getCurrentUserId();

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session state now externalized to Azure Cache for Redis
        // Spring Session Data Redis automatically stores session attributes in Redis instead of in-memory
        // This allows session data to be shared across all application instances
        // Azure Application Gateway can now distribute requests to any instance without session affinity
        // Session data persists even if an instance is terminated or scaled down
        session.setAttribute("lastBooking", booking); // cr-java-0065 - FIXED
        session.setAttribute("guestName", guestName); // cr-java-0065 - FIXED

        // FIXED cr-java-0067: Booking is now automatically cached in Azure Cache for Redis
        // @CachePut annotation stores the booking in Redis with TTL of 1 hour
        // Cache key is the bookingId, cache value is the entire booking map
        // This replaces the previous static HashMap that caused memory growth and scaling issues

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("authenticatedUser", authenticatedUser);
        response.put("userId", userId);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    @Cacheable(value = "bookings", key = "#bookingId")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            Authentication authentication,
            HttpSession session) {

        // FIXED cr-java-0065: Session data now retrieved from Azure Cache for Redis
        // Spring Session ensures session attributes are available across all instances
        // No session affinity required - any instance can serve any request
        // Session data is consistent across the entire application cluster
        String lastGuest = (String) session.getAttribute("guestName"); // cr-java-0065 - FIXED
        
        // FIXED cr-java-0090: Include Azure AD user context in response
        String authenticatedUser = authHelper.getCurrentUserEmail();

        // FIXED cr-java-0067: Booking data is now retrieved from Azure Cache for Redis
        // @Cacheable annotation checks Redis cache first before calling the service
        // If booking is in cache and not expired, it's returned immediately
        // If not in cache, service is called and result is cached for future requests
        // This reduces database load and improves response time

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("authenticatedUser", authenticatedUser);
        result.put("authenticated", authHelper.isAuthenticated());
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Replaced hard-coded URL with externalized configuration
        // URL is now loaded from application.properties via @Value annotation
        // In production, migrate to Azure App Configuration for centralized config management
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP call to
        // internal inventory service. AWS ALB, WAF, and Well-Architected security review
        // enforce HTTPS. This call will be blocked or flagged in a cloud-native setup.
        String inventoryUrl = inventoryServiceUrl + "/rooms/available"; // cr-java-0088 (FIXED cr-java-0071)

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

    /**
     * FIXED cr-java-0067: Cache eviction endpoint for administrative purposes
     * 
     * Allows administrators to manually clear cached booking data.
     * Useful for cache invalidation when booking data is updated externally.
     * 
     * @param bookingId The booking ID to evict from cache
     * @return Response indicating cache eviction status
     */
    @DeleteMapping("/cache/{bookingId}")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = "bookings", key = "#bookingId")
    public Map<String, Object> evictBookingCache(@PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "cache_evicted");
        response.put("bookingId", bookingId);
        response.put("message", "Booking cache entry evicted from Azure Cache for Redis");
        return response;
    }

    /**
     * FIXED cr-java-0067: Clear all booking cache entries
     * 
     * Allows administrators to clear the entire booking cache.
     * Useful for cache refresh or troubleshooting.
     * 
     * @return Response indicating cache clear status
     */
    @DeleteMapping("/cache")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = "bookings", allEntries = true)
    public Map<String, Object> clearBookingCache() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "cache_cleared");
        response.put("message", "All booking cache entries cleared from Azure Cache for Redis");
        return response;
    }
}
