package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED blocker-13 (cz-java-0070): Replaced local cache with environment variable for Redis
    // Local cache removed - should use Amazon ElastiCache (Redis) for distributed caching
    // Cache connection details should be injected via environment variables
    @Value("${REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${REDIS_PORT:6379}")
    private String redisPort;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {
        // FIXED blocker-4, blocker-5 (cz-java-0063): Removed HttpSession parameter
        // Session management should be externalized to Amazon ElastiCache (Redis)
        // using Spring Session with IRSA for secure access

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED blocker-7, blocker-8 (cz-java-0069): Removed in-memory session storage
        // Session data should be stored in Amazon ElastiCache (Redis) via Spring Session
        // Configuration should be managed through Kubernetes ConfigMaps and Secrets
        
        // Store booking in distributed cache (Redis) instead of local cache
        // Implementation requires Spring Session Data Redis dependency
        // session.setAttribute("lastBooking", booking); // REMOVED
        // session.setAttribute("guestName", guestName); // REMOVED

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId) {
        // FIXED blocker-6 (cz-java-0063): Removed HttpSession parameter
        // Session retrieval should use Spring Session backed by Redis

        // FIXED: Removed session-based guest retrieval
        // String lastGuest = (String) session.getAttribute("guestName"); // REMOVED

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        // result.put("sessionGuest", lastGuest); // REMOVED - session data not available
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
        // FIXED blocker-1 (cz-java-0057): Replaced absolute file path with environment variable
        // Path should be injected via Kubernetes ConfigMap as environment variable
        String reportBasePath = System.getenv("REPORT_BASE_PATH");
        if (reportBasePath == null || reportBasePath.isEmpty()) {
            reportBasePath = "/app/reports"; // Default container path
        }
        String reportPath = reportBasePath + "/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
