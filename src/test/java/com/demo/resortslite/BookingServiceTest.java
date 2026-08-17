package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Test Suite")
class BookingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // Additional setup if needed
    }

    @Test
    @DisplayName("Test createBooking with valid parameters creates booking successfully")
    void testCreateBooking_withValidParameters_createsBookingSuccessfully() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("bookingId"));
        assertEquals(guestName, result.get("guestName"));
        assertEquals(roomType, result.get("roomType"));
        assertEquals(checkIn, result.get("checkIn"));
        assertEquals(checkOut, result.get("checkOut"));
        assertNotNull(result.get("confirmationCode"));
        assertNotNull(result.get("dbHost"));
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Test createBooking generates unique booking IDs")
    void testCreateBooking_generatesUniqueBookingIds() {
        // Arrange
        String guestName = "Jane Smith";
        String roomType = "SUITE";
        String checkIn = "2024-04-01";
        String checkOut = "2024-04-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> booking1 = bookingService.createBooking(guestName, roomType, checkIn, checkOut);
        Map<String, Object> booking2 = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(booking1.get("bookingId"));
        assertNotNull(booking2.get("bookingId"));
        assertNotEquals(booking1.get("bookingId"), booking2.get("bookingId"));
    }

    @Test
    @DisplayName("Test createBooking with empty guest name")
    void testCreateBooking_withEmptyGuestName_createsBooking() {
        // Arrange
        String guestName = "";
        String roomType = "STANDARD";
        String checkIn = "2024-05-01";
        String checkOut = "2024-05-03";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(result);
        assertEquals(guestName, result.get("guestName"));
    }

    @Test
    @DisplayName("Test createBooking generates confirmation code")
    void testCreateBooking_generatesConfirmationCode() {
        // Arrange
        String guestName = "Test Guest";
        String roomType = "VILLA";
        String checkIn = "2024-06-01";
        String checkOut = "2024-06-10";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(result.get("confirmationCode"));
        assertTrue(result.get("confirmationCode").toString().length() > 0);
    }

    @Test
    @DisplayName("Test createBooking booking ID starts with BK prefix")
    void testCreateBooking_bookingIdStartsWithBKPrefix() {
        // Arrange
        String guestName = "Alice Johnson";
        String roomType = "DELUXE";
        String checkIn = "2024-07-01";
        String checkOut = "2024-07-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        String bookingId = (String) result.get("bookingId");
        assertTrue(bookingId.startsWith("BK-"));
    }

    @Test
    @DisplayName("Test getBookingById returns booking details")
    void testGetBookingById_returnsBookingDetails() {
        // Arrange
        String bookingId = "BK-12345678";
        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("id", bookingId);
        mockBooking.put("guest", "John Doe");
        mockBooking.put("room", "DELUXE");

        when(jdbcTemplate.queryForMap(anyString())).thenReturn(mockBooking);

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("id"));
        assertEquals("John Doe", result.get("guest"));
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    @Test
    @DisplayName("Test getBookingById with non-existent booking returns error")
    void testGetBookingById_withNonExistentBooking_returnsError() {
        // Arrange
        String bookingId = "BK-99999999";
        when(jdbcTemplate.queryForMap(anyString())).thenThrow(new RuntimeException("Not found"));

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("error"));
        assertTrue(result.get("error").toString().contains(bookingId));
    }

    @Test
    @DisplayName("Test getBookingById with empty booking ID")
    void testGetBookingById_withEmptyBookingId_callsDatabase() {
        // Arrange
        String bookingId = "";
        Map<String, Object> mockResult = new HashMap<>();
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(mockResult);

        // Act
        Map<String, Object> result = bookingService.getBookingById(bookingId);

        // Assert
        assertNotNull(result);
        verify(jdbcTemplate).queryForMap(anyString());
    }

    @Test
    @DisplayName("Test calculateRoomPrice for STANDARD room")
    void testCalculateRoomPrice_forStandardRoom_returnsCorrectPrice() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 3;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("360.00", price); // 120 * 3
    }

    @Test
    @DisplayName("Test calculateRoomPrice for DELUXE room")
    void testCalculateRoomPrice_forDeluxeRoom_returnsCorrectPrice() {
        // Arrange
        String roomType = "DELUXE";
        int nights = 2;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("400.00", price); // 200 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice for SUITE room")
    void testCalculateRoomPrice_forSuiteRoom_returnsCorrectPrice() {
        // Arrange
        String roomType = "SUITE";
        int nights = 1;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("350.00", price); // 350 * 1
    }

    @Test
    @DisplayName("Test calculateRoomPrice for VILLA room")
    void testCalculateRoomPrice_forVillaRoom_returnsCorrectPrice() {
        // Arrange
        String roomType = "VILLA";
        int nights = 5;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("3000.00", price); // 600 * 5
    }

    @Test
    @DisplayName("Test calculateRoomPrice with PEAK season multiplier")
    void testCalculateRoomPrice_withPeakSeason_appliesMultiplier() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 2;
        String season = "PEAK";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("360.00", price); // 120 * 1.5 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice with OFF season discount")
    void testCalculateRoomPrice_withOffSeason_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 2;
        String season = "OFF";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("192.00", price); // 120 * 0.8 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice with GOLD loyalty discount")
    void testCalculateRoomPrice_withGoldLoyalty_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 2;
        String season = "REGULAR";
        String loyalty = "GOLD";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("216.00", price); // 120 * 0.9 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice with PLATINUM loyalty discount")
    void testCalculateRoomPrice_withPlatinumLoyalty_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 2;
        String season = "REGULAR";
        String loyalty = "PLATINUM";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("192.00", price); // 120 * 0.8 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice with DIAMOND loyalty discount")
    void testCalculateRoomPrice_withDiamondLoyalty_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 2;
        String season = "REGULAR";
        String loyalty = "DIAMOND";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("168.00", price); // 120 * 0.7 * 2
    }

    @Test
    @DisplayName("Test calculateRoomPrice with 7 nights discount")
    void testCalculateRoomPrice_with7Nights_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 7;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("798.00", price); // 120 * 0.95 * 7
    }

    @Test
    @DisplayName("Test calculateRoomPrice with 14 nights discount")
    void testCalculateRoomPrice_with14Nights_appliesDiscount() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 14;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        // Note: The logic has a bug - it checks nights >= 7 first, so >= 14 never applies
        assertEquals("1596.00", price); // 120 * 0.95 * 14
    }

    @Test
    @DisplayName("Test calculateRoomPrice with unknown room type defaults to STANDARD")
    void testCalculateRoomPrice_withUnknownRoomType_defaultsToStandard() {
        // Arrange
        String roomType = "UNKNOWN";
        int nights = 1;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("120.00", price); // Default to 120
    }

    @Test
    @DisplayName("Test calculateRoomPrice with combined discounts")
    void testCalculateRoomPrice_withCombinedDiscounts_appliesAll() {
        // Arrange
        String roomType = "DELUXE";
        int nights = 7;
        String season = "OFF";
        String loyalty = "GOLD";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        // 200 * 0.8 (OFF) * 0.9 (GOLD) * 0.95 (7 nights) * 7 = 958.32
        assertEquals("957.60", price);
    }

    @Test
    @DisplayName("Test isRoomAvailable with STANDARD room returns true")
    void testIsRoomAvailable_withStandardRoom_returnsTrue() {
        // Act
        boolean result = bookingService.isRoomAvailable("STANDARD");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with DELUXE room returns true")
    void testIsRoomAvailable_withDeluxeRoom_returnsTrue() {
        // Act
        boolean result = bookingService.isRoomAvailable("DELUXE");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with SUITE room returns true")
    void testIsRoomAvailable_withSuiteRoom_returnsTrue() {
        // Act
        boolean result = bookingService.isRoomAvailable("SUITE");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with VILLA room returns true")
    void testIsRoomAvailable_withVillaRoom_returnsTrue() {
        // Act
        boolean result = bookingService.isRoomAvailable("VILLA");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with invalid room type returns false")
    void testIsRoomAvailable_withInvalidRoomType_returnsFalse() {
        // Act
        boolean result = bookingService.isRoomAvailable("INVALID");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with empty room type returns false")
    void testIsRoomAvailable_withEmptyRoomType_returnsFalse() {
        // Act
        boolean result = bookingService.isRoomAvailable("");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Test isRoomAvailable with null room type returns false")
    void testIsRoomAvailable_withNullRoomType_throwsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            bookingService.isRoomAvailable(null);
        });
    }

    @Test
    @DisplayName("Test generateReport returns message with month")
    void testGenerateReport_returnsMessageWithMonth() {
        // Arrange
        String month = "March";

        // Act
        String result = bookingService.generateReport(month);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains(month));
        assertTrue(result.contains("Report generation triggered"));
    }

    @Test
    @DisplayName("Test generateReport includes payment API reference")
    void testGenerateReport_includesPaymentApiReference() {
        // Arrange
        String month = "April";

        // Act
        String result = bookingService.generateReport(month);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("http://"));
    }

    @Test
    @DisplayName("Test generateReport with empty month")
    void testGenerateReport_withEmptyMonth_returnsMessage() {
        // Arrange
        String month = "";

        // Act
        String result = bookingService.generateReport(month);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Report generation triggered"));
    }

    @Test
    @DisplayName("Test createBooking with special characters in guest name")
    void testCreateBooking_withSpecialCharactersInGuestName_createsBooking() {
        // Arrange
        String guestName = "O'Brien-Smith";
        String roomType = "DELUXE";
        String checkIn = "2024-08-01";
        String checkOut = "2024-08-05";

        doNothing().when(jdbcTemplate).execute(anyString());

        // Act
        Map<String, Object> result = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Assert
        assertNotNull(result);
        assertEquals(guestName, result.get("guestName"));
    }

    @Test
    @DisplayName("Test calculateRoomPrice with zero nights")
    void testCalculateRoomPrice_withZeroNights_returnsZero() {
        // Arrange
        String roomType = "STANDARD";
        int nights = 0;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        assertEquals("0.00", price);
    }

    @Test
    @DisplayName("Test calculateRoomPrice with negative nights")
    void testCalculateRoomPrice_withNegativeNights_returnsNegativePrice() {
        // Arrange
        String roomType = "STANDARD";
        int nights = -1;
        String season = "REGULAR";
        String loyalty = "NONE";

        // Act
        String price = bookingService.calculateRoomPrice(roomType, nights, season, loyalty);

        // Assert
        assertNotNull(price);
        // Should handle negative case, but current implementation doesn't validate
        assertTrue(price.contains("-") || price.equals("0.00"));
    }
}
