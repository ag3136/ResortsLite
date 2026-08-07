package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String inventoryUrl;
    private final Duration bookingCacheTtl;

    public BookingController(
            @Value("${app.inventory.endpoint}") String inventoryUrl,
            @Value("${BOOKING_CACHE_TTL_SECONDS:900}") long bookingCacheTtlSeconds) {
        this.inventoryUrl = inventoryUrl;
        this.bookingCacheTtl = Duration.ofSeconds(bookingCacheTtlSeconds);
    }

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);
        String resolvedSessionId = resolveSessionId(sessionId, (String) booking.get("bookingId"));

        redisTemplate.opsForValue().set(buildSessionKey(resolvedSessionId, "lastBooking"), booking.toString(), bookingCacheTtl);
        redisTemplate.opsForValue().set(buildSessionKey(resolvedSessionId, "guestName"), guestName, bookingCacheTtl);
        redisTemplate.opsForValue().set(buildBookingCacheKey((String) booking.get("bookingId")), booking.toString(), bookingCacheTtl);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("sessionId", resolvedSessionId);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        String lastGuest = sessionId == null ? null : redisTemplate.opsForValue().get(buildSessionKey(sessionId, "guestName"));

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        String reportName = month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportService.buildReportDownloadUrl(reportName));
        response.put("message", bookingService.generateReport(month));
        response.put("schedule", reportService.scheduleReportGeneration(reportName, Duration.ofMinutes(5)));
        return response;
    }

    private String resolveSessionId(String sessionId, String bookingId) {
        return sessionId == null || sessionId.trim().isEmpty() ? "booking-" + bookingId : sessionId;
    }

    private String buildSessionKey(String sessionId, String suffix) {
        return "session:" + sessionId + ":" + suffix;
    }

    private String buildBookingCacheKey(String bookingId) {
        return "booking-cache:" + bookingId;
    }
}
