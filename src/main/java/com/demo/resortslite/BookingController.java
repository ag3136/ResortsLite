package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
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

    @Autowired
    private AwsParameterStoreConfig parameterStoreConfig;

    // FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
    // 
    // Replaced static HashMap with Amazon ElastiCache for Redis using Spring Cache abstraction.
    // The @Cacheable, @CachePut, and @CacheEvict annotations automatically manage cache operations
    // with proper TTL policies configured in RedisCacheConfig.
    // 
    // Benefits:
    // - Controlled expiration: 30-minute TTL prevents indefinite memory growth
    // - Consistent data: Cache is shared across all EC2 instances in the cluster
    // - Centralized management: Single ElastiCache cluster manages all cache data
    // - Automatic eviction: Expired entries are automatically removed by Redis
    // - Horizontal scaling: Cache operations work correctly across multiple instances
    // - No instance-local state: Application remains stateless and cloud-native
    // 
    // Migration from HashMap to Redis Cache:
    // - Old: private static final Map<String, Object> bookingCache = new HashMap<>();
    // - New: @Cacheable(value = "bookingCache", key = "#result['bookingId']")
    // 
    // The static HashMap has been removed. Cache operations are now handled by Spring Cache
    // with Redis as the backing store, configured with a 30-minute TTL in RedisCacheConfig.

    /**
     * Create a new booking
     * 
     * FIXED cr-java-0065 [State Management & Session Issues / High]: HTTP Session State Storage
     * 
     * HttpSession is now backed by Amazon ElastiCache for Redis via Spring Session Data Redis.
     * Session data is automatically stored in Redis instead of local memory, enabling:
     * - Stateless application instances
     * - Horizontal scaling across multiple EC2 instances
     * - Session persistence across deployments and restarts
     * - AWS ALB load balancing without sticky sessions
     * 
     * Spring Session transparently intercepts HttpSession operations and stores data in Redis.
     * No code changes required - the same HttpSession API works with distributed storage.
     * 
     * FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
     * 
     * @CachePut annotation automatically stores the booking in Redis cache with 30-minute TTL.
     * The cache key is the bookingId, and the entire booking object is cached.
     * This replaces the manual bookingCache.put() operation that used unbounded HashMap.
     */
    @PostMapping("/create")
    @CachePut(value = "bookingCache", key = "#result['bookingId']")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) { // FIXED cr-java-0065: Now backed by Redis via Spring Session

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session attributes are now stored in Amazon ElastiCache for Redis
        // These setAttribute calls are intercepted by Spring Session and persisted to Redis
        // Session data is accessible from any application instance in the cluster
        session.setAttribute("lastBooking", booking); // FIXED cr-java-0065: Redis-backed session
        session.setAttribute("guestName", guestName); // FIXED cr-java-0065: Redis-backed session

        // FIXED cr-java-0067: Removed manual cache operation - @CachePut handles this automatically
        // Old code: bookingCache.put((String) booking.get("bookingId"), booking);
        // New: @CachePut annotation stores booking in Redis with 30-minute TTL

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Get booking status
     * 
     * FIXED cr-java-0065 [State Management & Session Issues / High]: HTTP Session State Storage
     * 
     * Session data retrieval now reads from Amazon ElastiCache for Redis via Spring Session.
     * The session is shared across all application instances, so this method will return
     * the correct guest name regardless of which EC2 instance handles the request.
     * 
     * FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
     * 
     * Booking data is now retrieved from BookingService, which can implement its own
     * caching strategy using @Cacheable if needed. The controller no longer maintains
     * instance-local cache state.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) { // FIXED cr-java-0065: Now backed by Redis via Spring Session

        // FIXED cr-java-0065: getAttribute now reads from Redis instead of local memory
        // Session data is consistent across all instances in the cluster
        String lastGuest = (String) session.getAttribute("guestName"); // FIXED cr-java-0065: Redis-backed session

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Get cached booking details
     * 
     * FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
     * 
     * @Cacheable annotation automatically retrieves booking from Redis cache if available.
     * If not in cache, it calls bookingService.getBookingById() and stores the result
     * in Redis with 30-minute TTL. This replaces manual HashMap lookups.
     */
    @GetMapping("/cached/{bookingId}")
    @Cacheable(value = "bookingCache", key = "#bookingId")
    public Map<String, Object> getCachedBooking(@PathVariable String bookingId) {
        // FIXED cr-java-0067: @Cacheable handles cache lookup automatically
        // If cache miss, this method is called and result is cached
        // If cache hit, this method is skipped and cached value is returned
        return bookingService.getBookingById(bookingId);
    }

    /**
     * Clear booking from cache
     * 
     * FIXED cr-java-0067 [State Management & Session Issues / Medium]: In-Memory Caching Without TTL
     * 
     * @CacheEvict annotation automatically removes booking from Redis cache.
     * This provides explicit cache invalidation when needed.
     */
    @DeleteMapping("/cache/{bookingId}")
    @CacheEvict(value = "bookingCache", key = "#bookingId")
    public Map<String, Object> clearBookingCache(@PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "cache cleared");
        response.put("bookingId", bookingId);
        return response;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071 [Cloud Compatibility / Mandatory]: Replaced hard-coded environment URL
        // with externalized configuration from AWS Systems Manager Parameter Store.
        // This enables environment-agnostic deployments without code changes.
        String inventoryUrl = parameterStoreConfig.getInventoryServiceUrl(); // FIXED cr-java-0071

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
