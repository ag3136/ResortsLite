package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

// FIXED cz-java-0069: HttpSession now backed by Spring Session with Redis (Amazon ElastiCache)
// Sessions are externalized and shared across all container instances for horizontal scaling
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.report.base.path:/var/reports}")
    private String reportBasePath;

    // FIXED cz-java-0070: Replaced local in-memory cache with Amazon ElastiCache (Redis)
    // Cache is now shared across all container instances for horizontal scaling on EKS
    private static final String CACHE_KEY_PREFIX = "booking:cache:";

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) { // FIXED cz-java-0069: Session now externalized to Redis

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cz-java-0069: Lines 34-35 - Session data now stored in Amazon ElastiCache (Redis)
        // instead of local memory. Survives container restarts and works across multiple instances.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // FIXED cz-java-0070: Store booking in Redis cache with 1-hour TTL
        // Cache is accessible from all container instances in the EKS cluster
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + booking.get("bookingId"), booking, 1, TimeUnit.HOURS);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) { // FIXED cz-java-0069: Session now externalized to Redis

        // FIXED cz-java-0069: Reading from externalized Redis session storage
        // Session data is now accessible from any container instance in the cluster
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
        // FIXED cz-java-0057: Replaced hardcoded absolute path with environment variable
        String reportPath = reportBasePath + "/" + month + "_bookings.pdf";

        // FIXED cz-java-0082: Decoupled report generation from BookingService to ReportService
        // This separates concerns and enables independent microservice deployment on EKS
        // ReportService can now be deployed as a separate microservice with its own resources
        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", reportService.generateMonthlyReport(month, "2024"));
        return response;
    }
}
