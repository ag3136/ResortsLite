package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SessionRepository sessionRepository;

    // VIOLATION cr-java-0067 [Cloud Compatibility / Mandatory]: In-memory cache without TTL
    // breaks horizontal scaling — cache is instance-local, invisible to other EC2 instances
    private static final Map<String, Object> bookingCache = new HashMap<>(); // cr-java-0067

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam(required = false) String sessionId) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIX blocker-1, blocker-2, blocker-3, blocker-4, blocker-5: Replaced HttpSession with
        // Spring Session Data Redis (SessionRepository) for distributed, Redis-backed session storage.
        // Session state is now persisted in Azure Cache for Redis, enabling stateless horizontal
        // scaling across multiple container instances in AKS or Azure Container Apps.
        Session session = sessionRepository.createSession();
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);
        sessionRepository.save(session);

        bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        response.put("sessionId", session.getId());
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestParam(required = false) String sessionId) {

        // FIX blocker-1, blocker-2, blocker-3: Reading session state from Redis-backed
        // SessionRepository instead of in-memory HttpSession. Session data is now available
        // across all container instances via Azure Cache for Redis.
        String lastGuest = null;
        if (sessionId != null) {
            Session session = sessionRepository.findById(sessionId);
            if (session != null) {
                lastGuest = session.getAttribute("guestName");
            }
        }

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
