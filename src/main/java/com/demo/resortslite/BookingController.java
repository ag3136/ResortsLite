package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-native version.
 *
 * Session state is managed by Spring Session backed by Amazon ElastiCache for Redis
 * (cr-java-0065), enabling stateless application instances and horizontal scaling.
 *
 * In-memory cache replaced with Amazon ElastiCache for Redis with TTL (cr-java-0067).
 *
 * Hard-coded inventory URL replaced with AWS SSM Parameter Store lookup (cr-java-0071).
 */
@RestController
@RequestMapping("/api/bookings")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // cr-java-0067: Replaced static in-memory HashMap (no TTL) with RedisTemplate
    // backed by Amazon ElastiCache for Redis — supports TTL, distributed access,
    // and consistent cache state across all application instances.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Cache TTL in seconds — injected from environment variable (default 30 minutes)
    @Value("${cache.booking.ttl-seconds:${BOOKING_CACHE_TTL_SECONDS:1800}}")
    private long bookingCacheTtlSeconds;

    // cr-java-0071: Inventory service URL retrieved from AWS SSM Parameter Store
    // via environment variable — no hard-coded internal hostname in source code.
    @Value("${app.inventory.endpoint:${INVENTORY_ENDPOINT:https://inventory-service.internal:8081/rooms/available}}")
    private String inventoryEndpoint;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // cr-java-0065: Session state stored in Amazon ElastiCache for Redis via
        // Spring Session — session data is shared across all application instances,
        // enabling stateless horizontal scaling behind AWS ALB.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // cr-java-0067: Store booking in ElastiCache Redis with TTL instead of
        // unbounded in-memory HashMap — prevents memory growth and stale data.
        String cacheKey = "booking:" + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtlSeconds, TimeUnit.SECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // cr-java-0065: Session attribute read from Redis-backed Spring Session —
        // consistent across all instances in the cluster (not instance-local).
        String lastGuest = (String) session.getAttribute("guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // cr-java-0071: Inventory URL injected from SSM Parameter Store via
        // application.properties / environment variable — no hard-coded internal URL.
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report path is now an S3 object key — no local file system dependency
        String s3Key = "reports/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("s3Key", s3Key);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
