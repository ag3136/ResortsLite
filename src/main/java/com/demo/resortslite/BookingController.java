package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // blocker-13 (cz-java-0070): Replaced local in-memory HashMap cache with distributed
    // Redis-backed cache via RedisTemplate to support horizontal scaling across container instances.
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // blocker-9 (cz-java-0082): Decoupled tightly-coupled BookingService reference by
    // externalising the report service URL to an environment variable, enabling independent
    // deployment as a microservice via AWS App Mesh / API Gateway.
    @Value("${REPORT_SERVICE_URL:http://report-service/api/reports}")
    private String reportServiceUrl;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // blocker-4 / blocker-5 / blocker-6 (cz-java-0063) &
        // blocker-7 / blocker-8 (cz-java-0069): Replaced in-memory HttpSession storage with
        // Spring Session backed by Amazon ElastiCache for Redis. Session data is now stored
        // in Redis, enabling persistence across container restarts and horizontal scaling.
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);

        // blocker-13 (cz-java-0070): Store booking in distributed Redis cache instead of
        // local HashMap, ensuring cache coherence across all container instances.
        redisTemplate.opsForValue().set("booking:" + booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // blocker-4 / blocker-5 / blocker-6 (cz-java-0063): Session attribute retrieval now
        // uses Spring Session with Redis backend — works correctly across all cluster instances.
        String lastGuest = (String) session.getAttribute("guestName");

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
        // "/var/legacy/reports/<month>_bookings.pdf" with an Amazon S3 object key
        // constructed from the S3 bucket name supplied via the S3_REPORT_BUCKET
        // environment variable, eliminating filesystem dependency in containers.
        String s3Bucket = System.getenv().getOrDefault("S3_REPORT_BUCKET", "resorts-reports-bucket");
        String s3Key = "reports/" + month + "_bookings.pdf";
        String reportPath = "s3://" + s3Bucket + "/" + s3Key;

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
