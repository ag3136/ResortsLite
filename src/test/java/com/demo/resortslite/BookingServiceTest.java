package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for BookingService.
 * Tests all public methods, business logic, edge cases, and error scenarios.
 */
class BookingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("createBooking - should create booking with valid inputs")
    void testCreateBooking_withValidInputs_returnsBooking() {
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        assertNotNull(booking);
        assertNotNull(booking.get("bookingId"));
        assertEquals(guestName, booking.get("guestName"));
        assertEquals(roomType, booking.get("roomType"));
        assertEquals(checkIn, booking.get("checkIn"));
        assertEquals(checkOut, booking.get("checkOut"));
        assertNotNull(booking.get("confirmationCode"));
        assertNotNull(booking.get("dbHost"));
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("createBooking - should generate unique booking IDs")
    void testCreateBooking_shouldGenerateUniqueBookingIds() {
        doNothing().when(jdbcTemplate).execute(anyString());

        Map<String, Object> booking1 = bookingService.createBooking("Guest1", "SUITE", "2024-03-01", "2024-03-05");
        Map<String, Object> booking2 = bookingService.createBooking("Guest2", "DELUXE", "2024-03-10", "2024-03-15");

        assertNotEquals(booking1.get("bookingId"), booking2.get("bookingId"));
    }

    @Test
    @DisplayName("getBookingById - should return booking for valid ID")
    void testGetBookingById_withValidId_returnsBooking() {
        String bookingId = "BK-12345678";
        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("id", bookingId);
        mockBooking.put("guest", "John Doe");
        mockBooking.put("room", "DELUXE");

        when(jdbcTemplate.queryForMap(anyString())).thenReturn(mockBooking);

        Map<String, Object> result = bookingService.getBookingById(bookingId);

        assertNotNull(result);
        assertEquals(bookingId, result.get("id"));
        assertEquals("John Doe", result.get("guest"));
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    @Test
    @DisplayName("getBookingById - should handle non-existent booking ID")
    void testGetBookingById_withNonExistentId_returnsError() {
        String bookingId = "BK-99999999";
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("Not found"));

        Map<String, Object> result = bookingService.getBookingById(bookingId);

        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).contains(bookingId));
    }

    @Test
    @DisplayName("calculateRoomPrice - should calculate STANDARD room price correctly")
    void testCalculateRoomPrice_forStandardRoom_returnsCorrectPrice() {
        String price = bookingService.calculateRoomPrice("STANDARD", 3, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("360.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should calculate DELUXE room price correctly")
    void testCalculateRoomPrice_forDeluxeRoom_returnsCorrectPrice() {
        String price = bookingService.calculateRoomPrice("DELUXE", 2, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("400.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should calculate SUITE room price correctly")
    void testCalculateRoomPrice_forSuiteRoom_returnsCorrectPrice() {
        String price = bookingService.calculateRoomPrice("SUITE", 5, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("1750.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should calculate VILLA room price correctly")
    void testCalculateRoomPrice_forVillaRoom_returnsCorrectPrice() {
        String price = bookingService.calculateRoomPrice("VILLA", 4, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("2400.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply PEAK season multiplier")
    void testCalculateRoomPrice_withPeakSeason_appliesMultiplier() {
        String price = bookingService.calculateRoomPrice("STANDARD", 2, "PEAK", "NONE");
        assertNotNull(price);
        assertEquals("360.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply OFF season discount")
    void testCalculateRoomPrice_withOffSeason_appliesDiscount() {
        String price = bookingService.calculateRoomPrice("STANDARD", 2, "OFF", "NONE");
        assertNotNull(price);
        assertEquals("192.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply GOLD loyalty discount")
    void testCalculateRoomPrice_withGoldLoyalty_appliesDiscount() {
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "GOLD");
        assertNotNull(price);
        assertEquals("540.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply PLATINUM loyalty discount")
    void testCalculateRoomPrice_withPlatinumLoyalty_appliesDiscount() {
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "PLATINUM");
        assertNotNull(price);
        assertEquals("480.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply DIAMOND loyalty discount")
    void testCalculateRoomPrice_withDiamondLoyalty_appliesDiscount() {
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "REGULAR", "DIAMOND");
        assertNotNull(price);
        assertEquals("420.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should apply 7+ nights discount")
    void testCalculateRoomPrice_with7Nights_appliesDiscount() {
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("798.00", price);
    }

    @Test
    @DisplayName("calculateRoomPrice - should handle unknown room type")
    void testCalculateRoomPrice_withUnknownRoomType_usesDefaultPrice() {
        String price = bookingService.calculateRoomPrice("UNKNOWN", 2, "REGULAR", "NONE");
        assertNotNull(price);
        assertEquals("240.00", price);
    }

    @Test
    @DisplayName("isRoomAvailable - should return true for STANDARD room")
    void testIsRoomAvailable_forStandardRoom_returnsTrue() {
        boolean available = bookingService.isRoomAvailable("STANDARD");
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable - should return true for DELUXE room")
    void testIsRoomAvailable_forDeluxeRoom_returnsTrue() {
        boolean available = bookingService.isRoomAvailable("DELUXE");
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable - should return true for SUITE room")
    void testIsRoomAvailable_forSuiteRoom_returnsTrue() {
        boolean available = bookingService.isRoomAvailable("SUITE");
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable - should return true for VILLA room")
    void testIsRoomAvailable_forVillaRoom_returnsTrue() {
        boolean available = bookingService.isRoomAvailable("VILLA");
        assertTrue(available);
    }

    @Test
    @DisplayName("isRoomAvailable - should return false for unknown room type")
    void testIsRoomAvailable_forUnknownRoom_returnsFalse() {
        boolean available = bookingService.isRoomAvailable("PRESIDENTIAL");
        assertFalse(available);
    }

    @Test
    @DisplayName("isRoomAvailable - should return false for empty room type")
    void testIsRoomAvailable_forEmptyRoomType_returnsFalse() {
        boolean available = bookingService.isRoomAvailable("");
        assertFalse(available);
    }

    @Test
    @DisplayName("generateReport - should return message with month")
    void testGenerateReport_withValidMonth_returnsMessage() {
        String result = bookingService.generateReport("2024-03");
        assertNotNull(result);
        assertTrue(result.contains("2024-03"));
        assertTrue(result.contains("Report generation triggered"));
    }
}
