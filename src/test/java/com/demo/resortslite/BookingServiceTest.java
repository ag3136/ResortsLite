package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    // -----------------------------------------------------------------------
    // createBooking tests
    // -----------------------------------------------------------------------

    @Test
    void createBooking_withValidInputs_returnsBookingMapWithExpectedKeys() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05");
        assertNotNull(result);
        assertTrue(result.containsKey("bookingId"));
        assertTrue(result.containsKey("guestName"));
        assertTrue(result.containsKey("roomType"));
        assertTrue(result.containsKey("checkIn"));
        assertTrue(result.containsKey("checkOut"));
        assertTrue(result.containsKey("confirmationCode"));
        assertTrue(result.containsKey("dbHost"));
    }

    @Test
    void createBooking_bookingIdStartsWithBKPrefix() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Bob", "DELUXE", "2024-07-01", "2024-07-03");
        String bookingId = (String) result.get("bookingId");
        assertNotNull(bookingId);
        assertTrue(bookingId.startsWith("BK-"), "bookingId should start with 'BK-'");
    }

    @Test
    void createBooking_confirmationCodeIsNonEmpty() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Carol", "STANDARD", "2024-08-10", "2024-08-12");
        String confirmCode = (String) result.get("confirmationCode");
        assertNotNull(confirmCode);
        assertFalse(confirmCode.isEmpty());
    }

    @Test
    void createBooking_guestNameStoredCorrectly() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Dave", "VILLA", "2024-09-01", "2024-09-10");
        assertEquals("Dave", result.get("guestName"));
    }

    @Test
    void createBooking_roomTypeStoredCorrectly() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Eve", "VILLA", "2024-09-01", "2024-09-10");
        assertEquals("VILLA", result.get("roomType"));
    }

    @Test
    void createBooking_checkInAndCheckOutStoredCorrectly() {
        doNothing().when(jdbcTemplate).execute(anyString());
        Map<String, Object> result = bookingService.createBooking(
                "Frank", "STANDARD", "2024-10-01", "2024-10-05");
        assertEquals("2024-10-01", result.get("checkIn"));
        assertEquals("2024-10-05", result.get("checkOut"));
    }

    @Test
    void createBooking_jdbcTemplateExecuteIsCalledOnce() {
        doNothing().when(jdbcTemplate).execute(anyString());
        bookingService.createBooking("Grace", "DELUXE", "2024-11-01", "2024-11-03");
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    // -----------------------------------------------------------------------
    // getBookingById tests
    // -----------------------------------------------------------------------

    @Test
    void getBookingById_whenFound_returnsResultMap() {
        Map<String, Object> dbRow = new HashMap<>();
        dbRow.put("id", "BK-12345678");
        dbRow.put("guest", "Henry");
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(dbRow);
        Map<String, Object> result = bookingService.getBookingById("BK-12345678");
        assertNotNull(result);
        assertEquals("Henry", result.get("guest"));
    }

    @Test
    void getBookingById_whenNotFound_returnsErrorMap() {
        when(jdbcTemplate.queryForMap(anyString()))
                .thenThrow(new RuntimeException("No results"));
        Map<String, Object> result = bookingService.getBookingById("BK-NOTEXIST");
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        String errorMsg = (String) result.get("error");
        assertTrue(errorMsg.contains("BK-NOTEXIST"));
    }

    @Test
    void getBookingById_errorMessageContainsBookingNotFound() {
        when(jdbcTemplate.queryForMap(anyString()))
                .thenThrow(new RuntimeException("EmptyResultDataAccessException"));
        Map<String, Object> result = bookingService.getBookingById("BK-UNKNOWN");
        String errorMsg = (String) result.get("error");
        assertTrue(errorMsg.startsWith("Booking not found:"));
    }

    // -----------------------------------------------------------------------
    // calculateRoomPrice tests
    // -----------------------------------------------------------------------

    @Test
    void calculateRoomPrice_standardRoomNoSeasonNoLoyalty_returnsBaseTimesNights() {
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "NONE");
        assertEquals("120.00", price);
    }

    @Test
    void calculateRoomPrice_deluxeRoomNoSeasonNoLoyalty() {
        String price = bookingService.calculateRoomPrice("DELUXE", 1, "NORMAL", "NONE");
        assertEquals("200.00", price);
    }

    @Test
    void calculateRoomPrice_suiteRoomNoSeasonNoLoyalty() {
        String price = bookingService.calculateRoomPrice("SUITE", 1, "NORMAL", "NONE");
        assertEquals("350.00", price);
    }

    @Test
    void calculateRoomPrice_villaRoomNoSeasonNoLoyalty() {
        String price = bookingService.calculateRoomPrice("VILLA", 1, "NORMAL", "NONE");
        assertEquals("600.00", price);
    }

    @Test
    void calculateRoomPrice_unknownRoomTypeDefaultsToStandard() {
        String price = bookingService.calculateRoomPrice("PENTHOUSE", 1, "NORMAL", "NONE");
        assertEquals("120.00", price);
    }

    @Test
    void calculateRoomPrice_peakSeasonApplies150PercentMultiplier() {
        // STANDARD base=120, PEAK *1.5 = 180, 1 night = 180.00
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "PEAK", "NONE");
        assertEquals("180.00", price);
    }

    @Test
    void calculateRoomPrice_offSeasonApplies80PercentMultiplier() {
        // STANDARD base=120, OFF *0.8 = 96, 1 night = 96.00
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "OFF", "NONE");
        assertEquals("96.00", price);
    }

    @Test
    void calculateRoomPrice_goldLoyaltyApplies10PercentDiscount() {
        // STANDARD base=120, GOLD *0.9 = 108, 1 night = 108.00
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "GOLD");
        assertEquals("108.00", price);
    }

    @Test
    void calculateRoomPrice_platinumLoyaltyApplies20PercentDiscount() {
        // STANDARD base=120, PLATINUM *0.8 = 96, 1 night = 96.00
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "PLATINUM");
        assertEquals("96.00", price);
    }

    @Test
    void calculateRoomPrice_diamondLoyaltyApplies30PercentDiscount() {
        // STANDARD base=120, DIAMOND *0.7 = 84, 1 night = 84.00
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "DIAMOND");
        assertEquals("84.00", price);
    }

    @Test
    void calculateRoomPrice_sevenNightsApplies5PercentDiscount() {
        // STANDARD base=120, 7 nights *0.95 = 114, total = 114*7 = 798.00
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "NORMAL", "NONE");
        assertEquals("798.00", price);
    }

    @Test
    void calculateRoomPrice_fourteenNightsApplies10PercentDiscount() {
        // STANDARD base=120, 14 nights *0.90 = 108, total = 108*14 = 1512.00
        String price = bookingService.calculateRoomPrice("STANDARD", 14, "NORMAL", "NONE");
        assertEquals("1512.00", price);
    }

    @Test
    void calculateRoomPrice_fifteenNightsApplies10PercentDiscount() {
        // STANDARD base=120, 15 nights *0.90 = 108, total = 108*15 = 1620.00
        String price = bookingService.calculateRoomPrice("STANDARD", 15, "NORMAL", "NONE");
        assertEquals("1620.00", price);
    }

    @Test
    void calculateRoomPrice_sixNightsNoLongStayDiscount() {
        // STANDARD base=120, 6 nights no discount, total = 120*6 = 720.00
        String price = bookingService.calculateRoomPrice("STANDARD", 6, "NORMAL", "NONE");
        assertEquals("720.00", price);
    }

    @Test
    void calculateRoomPrice_peakSeasonWithDiamondLoyalty() {
        // SUITE base=350, PEAK *1.5=525, DIAMOND *0.7=367.5, 2 nights = 735.00
        String price = bookingService.calculateRoomPrice("SUITE", 2, "PEAK", "DIAMOND");
        assertEquals("735.00", price);
    }

    @Test
    void calculateRoomPrice_offSeasonWithGoldLoyaltyAndLongStay() {
        // DELUXE base=200, OFF *0.8=160, GOLD *0.9=144, 14 nights *0.90=129.6, total=129.6*14=1814.40
        String price = bookingService.calculateRoomPrice("DELUXE", 14, "OFF", "GOLD");
        assertEquals("1814.40", price);
    }

    @Test
    void calculateRoomPrice_multipleNightsReturnsTotalNotPerNight() {
        // STANDARD base=120, 3 nights = 360.00
        String price = bookingService.calculateRoomPrice("STANDARD", 3, "NORMAL", "NONE");
        assertEquals("360.00", price);
    }

    // -----------------------------------------------------------------------
    // isRoomAvailable tests
    // -----------------------------------------------------------------------

    @Test
    void isRoomAvailable_standardRoomIsAvailable() {
        assertTrue(bookingService.isRoomAvailable("STANDARD"));
    }

    @Test
    void isRoomAvailable_deluxeRoomIsAvailable() {
        assertTrue(bookingService.isRoomAvailable("DELUXE"));
    }

    @Test
    void isRoomAvailable_suiteRoomIsAvailable() {
        assertTrue(bookingService.isRoomAvailable("SUITE"));
    }

    @Test
    void isRoomAvailable_villaRoomIsAvailable() {
        assertTrue(bookingService.isRoomAvailable("VILLA"));
    }

    @Test
    void isRoomAvailable_unknownRoomTypeReturnsFalse() {
        assertFalse(bookingService.isRoomAvailable("PENTHOUSE"));
    }

    @Test
    void isRoomAvailable_emptyStringReturnsFalse() {
        assertFalse(bookingService.isRoomAvailable(""));
    }

    @Test
    void isRoomAvailable_lowercaseRoomTypeReturnsFalse() {
        assertFalse(bookingService.isRoomAvailable("standard"));
    }

    @Test
    void isRoomAvailable_nullRoomTypeThrowsNullPointerException() {
        // The source switch statement throws NullPointerException for null input in Java 11
        // (switch on null is undefined behaviour pre-Java 14).
        assertThrows(NullPointerException.class,
                () -> bookingService.isRoomAvailable(null));
    }

    // -----------------------------------------------------------------------
    // generateReport tests
    // -----------------------------------------------------------------------

    @Test
    void generateReport_returnsNonNullMessage() {
        String result = bookingService.generateReport("2024-03");
        assertNotNull(result);
    }

    @Test
    void generateReport_messageContainsMonth() {
        String result = bookingService.generateReport("2024-03");
        assertTrue(result.contains("2024-03"));
    }

    @Test
    void generateReport_messageContainsReportGenerationText() {
        String result = bookingService.generateReport("2024-06");
        assertTrue(result.contains("Report generation triggered for:"));
    }

    @Test
    void generateReport_differentMonthsProduceDifferentMessages() {
        String result1 = bookingService.generateReport("2024-01");
        String result2 = bookingService.generateReport("2024-12");
        assertNotEquals(result1, result2);
    }
}
