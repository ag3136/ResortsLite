package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cloud-native booking controller with distributed session management using Azure Cache for Redis.
 * 
 * Fixes applied:
 * - cr-java-0065: Replaced HTTP session storage with Azure Cache for Redis
 * - cr-java-0067: Replaced in-memory cache with Azure Cache for Redis with TTL
 * - cr-java-0071: Externalized URLs to Azure App Configuration
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    private static final long CACHE_TTL_MINUTES = 30;
    private static final String CACHE_PREFIX = "booking:";
    private static final String SESSION_PREFIX = "session:";

    /**
     * Creates a new booking and stores state in Azure Cache for Redis.
     * Replaces HTTP session storage with distributed cache for horizontal scalability.
     * 
     * @param guestName Guest name
     * @param roomType Room type
     * @param checkIn Check-in date
     * @param checkOut Check-out date
     * @param sessionId Session identifier (from request header or cookie)
     * @return Booking confirmation response
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Store booking state in Azure Cache for Redis instead of HTTP session
        if (redisTemplate != null && sessionId != null) {
            String sessionKey = SESSION_PREFIX + sessionId + ":lastBooking";
            String guestKey = SESSION_PREFIX + sessionId + ":guestName";
            
            redisTemplate.opsForValue().set(sessionKey, booking, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(guestKey, guestName, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        // Store booking in distributed cache with TTL
        if (redisTemplate != null) {
            String cacheKey = CACHE_PREFIX + booking.get("bookingId");
            redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("cacheType", "Azure Cache for Redis");
        return response;
    }

    /**
     * Retrieves booking status from Azure Cache for Redis.
     * 
     * @param bookingId Booking ID
     * @param sessionId Session identifier (from request header or cookie)
     * @return Booking status response
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // Retrieve guest name from Azure Cache for Redis instead of HTTP session
        String lastGuest = null;
        if (redisTemplate != null && sessionId != null) {
            String guestKey = SESSION_PREFIX + sessionId + ":guestName";
            lastGuest = (String) redisTemplate.opsForValue().get(guestKey);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        result.put("sessionType", "Azure Cache for Redis");
        return result;
    }

    /**
     * Checks room availability using externalized service URL.
     * 
     * @param roomType Room type
     * @return Availability response
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Use externalized URL from Azure App Configuration with HTTPS
        String inventoryUrl = inventoryServiceUrl;
        if (!inventoryUrl.startsWith("https://")) {
            inventoryUrl = inventoryUrl.replace("http://", "https://");
        }
        inventoryUrl += "/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        response.put("protocol", "HTTPS");
        return response;
    }

    /**
     * Downloads report from Azure Blob Storage.
     * 
     * @param month Month for the report
     * @return Report download response
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", bookingService.generateReport(month));
        response.put("storageType", "Azure Blob Storage");
        response.put("note", "Reports are stored in Azure Blob Storage, not local file system");
        return response;
    }

    /**
     * Retrieves booking from cache.
     * 
     * @param bookingId Booking ID
     * @return Cached booking or null
     */
    @GetMapping("/cache/{bookingId}")
    public Map<String, Object> getFromCache(@PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();
        
        if (redisTemplate != null) {
            String cacheKey = CACHE_PREFIX + bookingId;
            Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedBooking != null) {
                response.put("status", "cache_hit");
                response.put("booking", cachedBooking);
                response.put("cacheType", "Azure Cache for Redis");
            } else {
                response.put("status", "cache_miss");
                response.put("message", "Booking not found in cache or expired");
            }
        } else {
            response.put("status", "cache_unavailable");
            response.put("message", "Redis cache not configured");
        }
        
        return response;
    }
}
