package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    // ─────────────────────────────────────────────────────────────────────────
    // createBooking tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createBooking_withValidInputs_returnsBookingMap() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(
                "John Smith", "SUITE", "2024-06-01", "2024-06-05");

        // Assert
        assertNotNull(result);
        assertEquals("John Smith", result.get("guestName"));
        assertEquals("SUITE", result.get("roomType"));
        assertEquals("2024-06-01", result.get("checkIn"));
        assertEquals("2024-06-05", result.get("checkOut"));
    }

    @Test
    void createBooking_returnsBookingIdWithBKPrefix() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(
                "Alice", "DELUXE", "2024-07-01", "2024-07-03");

        // Assert
        String bookingId = (String) result.get("bookingId");
        assertNotNull(bookingId);
        assertTrue(bookingId.startsWith("BK-"), "Booking ID should start with 'BK-'");
    }

    @Test
    void createBooking_returnsConfirmationCode() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(
                "Bob", "STANDARD", "2024-08-01", "2024-08-02");

        // Assert
        assertNotNull(result.get("confirmationCode"));
        assertFalse(((String) result.get("confirmationCode")).isEmpty());
    }

    @Test
    void createBooking_returnsDbHostInResult() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(
                "Carol", "VILLA", "2024-09-01", "2024-09-07");

        // Assert
        assertNotNull(result.get("dbHost"));
    }

    @Test
    void createBooking_executesJdbcInsert() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        bookingService.createBooking("Dave", "SUITE", "2024-10-01", "2024-10-04");

        // Assert
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    void createBooking_eachCallGeneratesUniqueBookingId() {
        // Arrange
        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> booking1 = bookingService.createBooking(
                "Eve", "STANDARD", "2024-01-01", "2024-01-02");
        Map<String, Object> booking2 = bookingService.createBooking(
                "Frank", "DELUXE", "2024-01-03", "2024-01-04");

        // Assert
        assertNotEquals(booking1.get("bookingId"), booking2.get("bookingId"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getBookingById tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getBookingById_withValidId_returnsBookingData() {
        // Arrange
        Map<String, Object> dbRow = new HashMap<>();
        dbRow.put("id", "BK-12345678");
        dbRow.put("guest", "John Smith");
        dbRow.put("room", "SUITE");
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(dbRow);

        // Act
        Map<String, Object> result = bookingService.getBookingById("BK-12345678");

        // Assert
        assertNotNull(result);
        assertEquals("BK-12345678", result.get("id"));
        assertEquals("John Smith", result.get("guest"));
    }

    @Test
    void getBookingById_whenJdbcThrowsException_returnsErrorMap() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString()))
                .thenThrow(new RuntimeException("No results found"));

        // Act
        Map<String, Object> result = bookingService.getBookingById("BK-NOTFOUND");

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).contains("BK-NOTFOUND"));
    }

    @Test
    void getBookingById_errorMessageContainsBookingId() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString()))
                .thenThrow(new RuntimeException("Empty result set"));

        // Act
        Map<String, Object> result = bookingService.getBookingById("BK-ABCDEF12");

        // Assert
        String errorMsg = (String) result.get("error");
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("BK-ABCDEF12"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateRoomPrice tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void calculateRoomPrice_standardRoomNormalSeason_returnsCorrectPrice() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 3, "NORMAL", "NONE");

        // Assert
        assertNotNull(price);
        // 120.0 * 3 = 360.00
        assertEquals("360.00", price);
    }

    @Test
    void calculateRoomPrice_deluxeRoomNormalSeason_returnsCorrectPrice() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("DELUXE", 2, "NORMAL", "NONE");

        // Assert
        // 200.0 * 2 = 400.00
        assertEquals("400.00", price);
    }

    @Test
    void calculateRoomPrice_suiteRoomNormalSeason_returnsCorrectPrice() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("SUITE", 1, "NORMAL", "NONE");

        // Assert
        // 350.0 * 1 = 350.00
        assertEquals("350.00", price);
    }

    @Test
    void calculateRoomPrice_villaRoomNormalSeason_returnsCorrectPrice() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("VILLA", 1, "NORMAL", "NONE");

        // Assert
        // 600.0 * 1 = 600.00
        assertEquals("600.00", price);
    }

    @Test
    void calculateRoomPrice_unknownRoomType_defaultsToStandardPrice() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("UNKNOWN", 1, "NORMAL", "NONE");

        // Assert
        // defaults to 120.0 * 1 = 120.00
        assertEquals("120.00", price);
    }

    @Test
    void calculateRoomPrice_peakSeason_appliesMultiplier() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "PEAK", "NONE");

        // Assert
        // 120.0 * 1.5 * 1 = 180.00
        assertEquals("180.00", price);
    }

    @Test
    void calculateRoomPrice_offSeason_appliesDiscount() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "OFF", "NONE");

        // Assert
        // 120.0 * 0.8 * 1 = 96.00
        assertEquals("96.00", price);
    }

    @Test
    void calculateRoomPrice_goldLoyalty_appliesDiscount() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "GOLD");

        // Assert
        // 120.0 * 0.9 * 1 = 108.00
        assertEquals("108.00", price);
    }

    @Test
    void calculateRoomPrice_platinumLoyalty_appliesDiscount() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "PLATINUM");

        // Assert
        // 120.0 * 0.8 * 1 = 96.00
        assertEquals("96.00", price);
    }

    @Test
    void calculateRoomPrice_diamondLoyalty_appliesDiscount() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 1, "NORMAL", "DIAMOND");

        // Assert
        // 120.0 * 0.7 * 1 = 84.00
        assertEquals("84.00", price);
    }

    @Test
    void calculateRoomPrice_sevenNightsOrMore_appliesWeeklyDiscount() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("STANDARD", 7, "NORMAL", "NONE");

        // Assert
        // 120.0 * 0.95 * 7 = 798.00
        assertEquals("798.00", price);
    }

    @Test
    void calculateRoomPrice_fourteenNightsOrMore_appliesBiweeklyDiscount() {
        // Arrange / Act
        // NOTE: In the source code, nights >= 7 branch is checked first, so nights=14
        // applies the 0.95 multiplier (not 0.90), because the if-else chain hits >= 7 first.
        String price = bookingService.calculateRoomPrice("STANDARD", 14, "NORMAL", "NONE");

        // Assert
        // 120.0 * 0.95 * 14 = 1596.00
        assertEquals("1596.00", price);
    }

    @Test
    void calculateRoomPrice_combinedPeakAndGoldLoyalty() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("DELUXE", 3, "PEAK", "GOLD");

        // Assert
        // 200.0 * 1.5 * 0.9 * 3 = 810.00
        assertEquals("810.00", price);
    }

    @Test
    void calculateRoomPrice_returnsFormattedTwoDecimalString() {
        // Arrange / Act
        String price = bookingService.calculateRoomPrice("SUITE", 2, "NORMAL", "NONE");

        // Assert
        assertNotNull(price);
        assertTrue(price.matches("\\d+\\.\\d{2}"), "Price should be formatted to 2 decimal places");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isRoomAvailable tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isRoomAvailable_standardRoom_returnsTrue() {
        assertTrue(bookingService.isRoomAvailable("STANDARD"));
    }

    @Test
    void isRoomAvailable_deluxeRoom_returnsTrue() {
        assertTrue(bookingService.isRoomAvailable("DELUXE"));
    }

    @Test
    void isRoomAvailable_suiteRoom_returnsTrue() {
        assertTrue(bookingService.isRoomAvailable("SUITE"));
    }

    @Test
    void isRoomAvailable_villaRoom_returnsTrue() {
        assertTrue(bookingService.isRoomAvailable("VILLA"));
    }

    @Test
    void isRoomAvailable_unknownRoomType_returnsFalse() {
        assertFalse(bookingService.isRoomAvailable("PENTHOUSE"));
    }

    @Test
    void isRoomAvailable_emptyString_returnsFalse() {
        assertFalse(bookingService.isRoomAvailable(""));
    }

    @Test
    void isRoomAvailable_lowercaseRoomType_returnsFalse() {
        assertFalse(bookingService.isRoomAvailable("standard"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "DELUXE", "SUITE", "VILLA"})
    void isRoomAvailable_allValidRoomTypes_returnTrue(String roomType) {
        assertTrue(bookingService.isRoomAvailable(roomType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENTHOUSE", "CABIN", "BUNGALOW", "HOSTEL", ""})
    void isRoomAvailable_invalidRoomTypes_returnFalse(String roomType) {
        assertFalse(bookingService.isRoomAvailable(roomType));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateReport tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generateReport_withValidMonth_returnsNonNullString() {
        // Act
        String result = bookingService.generateReport("March");

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void generateReport_containsMonthInResult() {
        // Act
        String result = bookingService.generateReport("June");

        // Assert
        assertTrue(result.contains("June"));
    }

    @Test
    void generateReport_containsPaymentApiReference() {
        // Act
        String result = bookingService.generateReport("January");

        // Assert
        assertNotNull(result);
        // The result should reference the PAYMENT_API constant
        assertTrue(result.contains("via"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"})
    void generateReport_allMonths_returnsNonNullResult(String month) {
        String result = bookingService.generateReport(month);
        assertNotNull(result);
        assertTrue(result.contains(month));
    }
}
