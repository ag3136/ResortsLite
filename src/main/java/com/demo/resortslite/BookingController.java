package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Duration BOOKING_CACHE_TTL = Duration.ofMinutes(30);
    private static final String DEFAULT_INVENTORY_URL = "https://inventory-service.internal/rooms/available";

    @Autowired
    private BookingService bookingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);
        String bookingId = (String) booking.get("bookingId");

        redisTemplate.opsForValue().set("booking:lastBooking:" + bookingId, booking.toString(), BOOKING_CACHE_TTL);
        redisTemplate.opsForValue().set("booking:guestName:" + bookingId, guestName, BOOKING_CACHE_TTL);
        redisTemplate.opsForValue().set("booking:cache:" + bookingId, booking.toString(), BOOKING_CACHE_TTL);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(@PathVariable String bookingId) {
        String lastGuest = redisTemplate.opsForValue().get("booking:guestName:" + bookingId);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        String inventoryUrl = System.getenv().getOrDefault("APP_INVENTORY_ENDPOINT", DEFAULT_INVENTORY_URL);

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", "azure-blob://reports/" + month + "_bookings.pdf");
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
