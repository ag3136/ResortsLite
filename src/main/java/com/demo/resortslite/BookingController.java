package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // blocker-13 (cz-java-0070): Replaced local in-memory HashMap cache with Redis-backed
    // distributed cache using RedisTemplate to ensure cache consistency across scaled instances
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // blocker-4 (cz-java-0063) / blocker-5 (cz-java-0063) / blocker-6 (cz-java-0063):
    // Removed HttpSession import and usage; session state is now managed via
    // Spring Session Data Redis (auto-configured), making sessions distributed and
    // resilient to container restarts and horizontal scaling.

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // blocker-7 (cz-java-0069) / blocker-8 (cz-java-0069): Replaced in-memory
        // session.setAttribute calls with Redis-backed storage via RedisTemplate so that
        // session data persists across container restarts and is visible to all instances.
        String bookingId = (String) booking.get("bookingId");
        redisTemplate.opsForHash().put("session:lastBooking:" + bookingId, "booking", booking);
        redisTemplate.opsForHash().put("session:guestName:" + bookingId, "guestName", guestName);

        // blocker-13 (cz-java-0070): Store in Redis instead of local HashMap
        redisTemplate.opsForValue().set("bookingCache:" + bookingId, booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId) {

        // blocker-5 (cz-java-0063) / blocker-6 (cz-java-0063): Reading session state from
        // Redis instead of in-memory HttpSession — consistent across all container instances.
        String lastGuest = (String) redisTemplate.opsForHash().get("session:guestName:" + bookingId, "guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // blocker-1 (cz-java-0057): Replaced hardcoded absolute file path
        // "/var/legacy/reports/" with Azure Blob Storage container URL sourced from
        // environment variable AZURE_BLOB_REPORTS_URL, ensuring cross-platform
        // compatibility and ephemeral container support.
        String azureBlobReportsUrl = System.getenv("AZURE_BLOB_REPORTS_URL") != null
                ? System.getenv("AZURE_BLOB_REPORTS_URL")
                : "https://${AZURE_STORAGE_ACCOUNT}.blob.core.windows.net/reports";
        String reportPath = azureBlobReportsUrl + "/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    // blocker-9 (cz-java-0082): Decomposed tightly-coupled direct instantiation of
    // ReportService into a Spring-injected dependency, reducing coupling and enabling
    // independent deployment as a microservice in AKS.
    @Autowired
    private ReportService reportService;
}
