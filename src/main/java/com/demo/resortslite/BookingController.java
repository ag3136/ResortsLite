package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0067: Replaced in-memory HashMap with Amazon ElastiCache for Redis
    // This provides:
    // - Distributed caching across all EC2 instances in the cluster
    // - Automatic TTL-based expiration to prevent memory growth
    // - Cache consistency across horizontal scaling and auto-scaling events
    // - High availability and persistence options via ElastiCache
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Cache TTL configuration - booking cache entries expire after 1 hour
    @Value("${app.cache.booking.ttl.minutes:60}")
    private long bookingCacheTtlMinutes;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: HTTP session now backed by Amazon ElastiCache for Redis via Spring Session.
        // Session data is automatically serialized to Redis, enabling stateless application instances.
        // AWS ALB can distribute requests across any EC2 instance — session state is centralized and shared.
        // Spring Session transparently intercepts HttpSession calls and persists to Redis cluster.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cr-java-0067: Store booking in Redis with TTL instead of static HashMap
        String bookingId = (String) booking.get("bookingId");
        String cacheKey = "booking:" + bookingId;
        redisTemplate.opsForValue().set(cacheKey, booking, bookingCacheTtlMinutes, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session data retrieved from Amazon ElastiCache for Redis.
        // Spring Session automatically deserializes session attributes from Redis cluster.
        // This works consistently across all application instances in the AWS Auto Scaling group.
        String lastGuest = (String) session.getAttribute("guestName");

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
