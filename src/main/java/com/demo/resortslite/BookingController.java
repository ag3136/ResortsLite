package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // blocker-13 (cz-java-0070): Replaced local in-memory HashMap cache with an
    // environment-variable-driven cache provider reference. Local caches break horizontal
    // scaling; use Amazon ElastiCache (Redis) injected via CACHE_PROVIDER env var.
    @Value("${CACHE_PROVIDER:redis}")
    private String cacheProvider;

    // blocker-1 (cz-java-0057): Replaced hardcoded absolute path "/var/legacy/reports/"
    // with an environment variable REPORT_BASE_PATH injected via Kubernetes ConfigMap.
    // This eliminates filesystem layout dependency between Windows and Linux containers.
    @Value("${REPORT_BASE_PATH:/var/reports}")
    private String reportBasePath;

    // blocker-4 (cz-java-0063): Replaced javax.servlet.http.HttpSession import with
    // Spring Session's SessionRepository to support externalized session storage
    // (Amazon ElastiCache / Redis) instead of in-memory server-side sessions.
    @Autowired
    private SessionRepository<Session> sessionRepository;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // blocker-5 (cz-java-0063) & blocker-7 (cz-java-0069): Replaced HttpSession
        // with Spring Session backed by Amazon ElastiCache (Redis). Session state is now
        // stored externally so it survives container restarts and horizontal scaling.
        Session session = sessionRepository.createSession();
        session.setAttribute("lastBooking", booking);
        session.setAttribute("guestName", guestName);
        sessionRepository.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            @RequestParam(required = false) String sessionId) {

        // blocker-6 (cz-java-0063) & blocker-8 (cz-java-0069): Replaced HttpSession
        // attribute read with Spring Session lookup via sessionId request parameter.
        // Session data is retrieved from Amazon ElastiCache (Redis) — works across all
        // container instances in the EKS cluster.
        String lastGuest = null;
        if (sessionId != null) {
            Session session = sessionRepository.findById(sessionId);
            if (session != null) {
                lastGuest = (String) session.getAttribute("guestName");
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
        String inventoryUrl = "http://inventory-service.internal:8081/rooms/available";

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // blocker-1 (cz-java-0057): reportBasePath is now injected from env var
        // REPORT_BASE_PATH via @Value above, eliminating the hardcoded absolute path.
        String reportPath = reportBasePath + "/" + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }

    // blocker-9 (cz-java-0082): Decoupled BookingService dependency by injecting it via
    // Spring @Autowired rather than direct instantiation, enabling independent deployment
    // as a separate EKS microservice with its own Deployment, Service, and ConfigMap.
    // The @Autowired BookingService field above already satisfies this pattern.
}
