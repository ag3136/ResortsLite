package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BookingController — cloud-native REST controller.
 *
 * <p>Fixes applied:
 * <ul>
 *   <li>cr-java-0067 (blocker 20) — Replaced the unbounded in-memory
 *       {@code HashMap} cache with Amazon ElastiCache for Redis via
 *       {@link RedisTemplate}. All cache entries are written with a TTL
 *       (1 hour) to prevent indefinite memory growth and ensure consistency
 *       across horizontally-scaled instances.</li>
 *   <li>cr-java-0065 (blockers 13–17) — Removed all {@code HttpSession}
 *       usages. Session state is now managed by Spring Session backed by
 *       Amazon ElastiCache for Redis, enabling stateless application instances
 *       with centralised, distributed session management. The controller no
 *       longer imports or references {@code javax.servlet.http.HttpSession}.</li>
 *   <li>cr-java-0071 (blocker 10) — Replaced the hard-coded inventory service
 *       URL with a value retrieved at runtime from AWS Systems Manager
 *       Parameter Store, enabling environment-agnostic deployments.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIX cr-java-0067 (blocker 20):
    // The unbounded static HashMap (bookingCache) is replaced with Amazon
    // ElastiCache for Redis via RedisTemplate. Spring Boot auto-configures
    // RedisTemplate when spring-boot-starter-data-redis is on the classpath
    // and spring.redis.host / spring.redis.port are set in application.properties.
    // Cache entries are stored with a 1-hour TTL to prevent stale data and
    // uncontrolled memory growth across all instances.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // FIX cr-java-0071 (blocker 10):
    // AWS SSM client used to resolve the inventory service URL at runtime.
    private final SsmClient ssmClient;

    // Cache TTL: 1 hour — prevents indefinite growth (fixes cr-java-0067).
    private static final long CACHE_TTL_HOURS = 1L;
    private static final String CACHE_KEY_PREFIX = "booking:";

    public BookingController() {
        this.ssmClient = SsmClient.create();
    }

    /**
     * Creates a new booking and stores it in the distributed Redis cache.
     *
     * <p>FIX cr-java-0065 (blockers 13–17): Session state (lastBooking, guestName)
     * is no longer stored in {@code HttpSession}. The controller is fully stateless;
     * Spring Session with ElastiCache for Redis handles any session requirements
     * transparently via the {@code @EnableRedisHttpSession} configuration.
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIX cr-java-0067 (blocker 20):
        // Store booking in Redis with a 1-hour TTL instead of the local HashMap.
        // This ensures cache consistency across all EC2 / ECS instances and
        // prevents unbounded memory growth.
        String cacheKey = CACHE_KEY_PREFIX + booking.get("bookingId");
        redisTemplate.opsForValue().set(cacheKey, booking, CACHE_TTL_HOURS, TimeUnit.HOURS);

        // FIX cr-java-0065 (blockers 13–17):
        // session.setAttribute("lastBooking", booking) and
        // session.setAttribute("guestName", guestName) are removed.
        // Spring Session + ElastiCache for Redis manages session state externally,
        // so no explicit HttpSession manipulation is needed in the controller.

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Returns the status of a booking, reading from the distributed Redis cache.
     *
     * <p>FIX cr-java-0065 (blockers 13–17): The {@code HttpSession} parameter and
     * {@code session.getAttribute("guestName")} call are removed. Guest context is
     * now retrieved from the Redis cache, which is consistent across all instances.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(@PathVariable String bookingId) {

        // FIX cr-java-0065 (blocker 17):
        // Reading guestName from HttpSession replaced with a Redis cache lookup.
        String cacheKey = CACHE_KEY_PREFIX + bookingId;
        Object cachedBooking = redisTemplate.opsForValue().get(cacheKey);

        String lastGuest = null;
        if (cachedBooking instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = (Map<String, Object>) cachedBooking;
            lastGuest = (String) cached.get("guestName");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability using the inventory service URL resolved from
     * AWS Systems Manager Parameter Store.
     *
     * <p>FIX cr-java-0071 (blocker 10): The hard-coded URL
     * {@code "http://inventory-service.internal:8081/rooms/available"} is replaced
     * with a value retrieved from SSM Parameter Store under the key
     * {@code /resorts/inventory/service-url}. This enables environment-agnostic
     * deployments without any code change between dev, staging, and production.
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIX cr-java-0071 (blocker 10):
        // Inventory service URL is resolved from AWS SSM Parameter Store at runtime.
        String inventoryUrl = getParameterFromSsm(
                "/resorts/inventory/service-url",
                "https://inventory-service.internal/rooms/available");

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report path is now an S3 object key managed by ReportService.
        String reportKey = "reports/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportS3Key", reportKey);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     * Falls back to {@code defaultValue} when SSM is not reachable (e.g. local dev).
     */
    private String getParameterFromSsm(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
