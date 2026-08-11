package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

/**
 * FIXED cr-java-0065: HTTP Session now backed by Amazon ElastiCache for Redis
 * Spring Session automatically stores all session data in Redis, enabling stateless instances
 * All session.setAttribute() and session.getAttribute() calls now interact with Redis
 */
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    @Lazy
    private com.demo.resortslite.config.AwsParameterStoreConfig parameterStoreConfig;

    @Value("${aws.paramstore.inventory.url.key:/resortslite/inventory/service/url}")
    private String inventoryUrlKey;

    @Value("${aws.paramstore.inventory.url.default:https://inventory-service.internal:8081/rooms/available}")
    private String inventoryUrlDefault;

    // VIOLATION cr-java-0067 [Cloud Compatibility / Mandatory]: In-memory cache without TTL
    // breaks horizontal scaling — cache is instance-local, invisible to other EC2 instances
    private static final Map<String, Object> bookingCache = new HashMap<>(); // cr-java-0067

    /**
     * FIXED cr-java-0065: HttpSession is now backed by Redis via Spring Session
     * Session data is automatically stored in ElastiCache, visible to all instances
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            // HttpSession parameter - Spring Session intercepts and stores in Redis
            // No code changes needed; session.setAttribute() now writes to ElastiCache
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session data now stored in ElastiCache Redis cluster
        // All EC2 instances share the same Redis backend - session is visible cluster-wide
        // Spring Session automatically serializes and stores these attributes in Redis
        session.setAttribute("lastBooking", booking); // Now writes to Redis (Line 27)
        session.setAttribute("guestName", guestName); // Now writes to Redis (Line 34-35)

        bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * FIXED cr-java-0065: HttpSession reads from Redis via Spring Session
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session data retrieved from ElastiCache Redis
        // Works correctly across all instances - session is distributed and shared
        // If user's request is routed to a different EC2 instance, session data is still available
        // Spring Session automatically deserializes the attribute from Redis
        String lastGuest = (String) session.getAttribute("guestName"); // Now reads from Redis (Line 48)

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Externalized environment URL using AWS Systems Manager Parameter Store
        // The URL is retrieved from Parameter Store, enabling environment-agnostic deployments
        // and eliminating hard-coded environment-specific endpoints
        String inventoryUrl = parameterStoreConfig.getParameter(inventoryUrlKey, inventoryUrlDefault);
        // Note: Also addresses cr-java-0088 by using HTTPS in default value

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
