package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AzureRedisStateService azureRedisStateService;

    private final String inventoryUrl;
    private final String reportDownloadBaseUrl;

    public BookingController(
            @Value("${app.inventory.endpoint}") String inventoryUrl,
            @Value("${app.reports.download-base-url}") String reportDownloadBaseUrl) {
        this.inventoryUrl = inventoryUrl;
        this.reportDownloadBaseUrl = reportDownloadBaseUrl;
    }

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpServletRequest request) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);
        String sessionId = request.getSession().getId();

        azureRedisStateService.storeSessionAttribute(sessionId, "lastBooking", booking.get("bookingId").toString());
        azureRedisStateService.storeSessionAttribute(sessionId, "guestName", guestName);
        azureRedisStateService.storeBooking((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpServletRequest request) {

        String sessionId = request.getSession().getId();
        String lastGuest = azureRedisStateService.getSessionAttribute(sessionId, "guestName");

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        result.put("cachedBooking", azureRedisStateService.getBooking(bookingId));
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
        String reportPath = reportDownloadBaseUrl + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
