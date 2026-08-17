package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BookingController Test Suite")
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private BookingController bookingController;
    private HttpSession mockSession;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        bookingController = new BookingController();
        mockSession = mock(HttpSession.class);
        
        // Inject the mock service using reflection
        java.lang.reflect.Field field = BookingController.class.getDeclaredField("bookingService");
        field.setAccessible(true);
        field.set(bookingController, bookingService);
    }

    @Test
    @DisplayName("Test createBooking with valid parameters returns confirmed status")
    void testCreateBooking_withValidParameters_returnsConfirmedStatus() {
        // Arrange
        String guestName = "John Doe";
        String roomType = "DELUXE";
        String checkIn = "2024-03-01";
        String checkOut = "2024-03-05";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-12345678");
        mockBooking.put("guestName", guestName);
        mockBooking.put("roomType", roomType);
        mockBooking.put("checkIn", checkIn);
        mockBooking.put("checkOut", checkOut);

        when(bookingService.createBooking(guestName, roomType, checkIn, checkOut))
                .thenReturn(mockBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                guestName, roomType, checkIn, checkOut, mockSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
        assertNotNull(response.get("booking"));
        verify(bookingService, times(1)).createBooking(guestName, roomType, checkIn, checkOut);
    }

    @Test
    @DisplayName("Test getBookingStatus returns booking details")
    void testGetBookingStatus_returnsBookingDetails() {
        // Arrange
        String bookingId = "BK-12345678";
        String guestName = "John Doe";
        Map<String, Object> mockBookingDetails = new HashMap<>();
        mockBookingDetails.put("bookingId", bookingId);
        mockBookingDetails.put("status", "confirmed");

        when(mockSession.getAttribute("guestName")).thenReturn(guestName);
        when(bookingService.getBookingById(bookingId)).thenReturn(mockBookingDetails);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus(bookingId, mockSession);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(guestName, result.get("sessionGuest"));
        assertNotNull(result.get("details"));
        verify(bookingService, times(1)).getBookingById(bookingId);
    }

    @Test
    @DisplayName("Test checkAvailability returns room availability")
    void testCheckAvailability_returnsRoomAvailability() {
        // Arrange
        String roomType = "DELUXE";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability(roomType);

        // Assert
        assertNotNull(response);
        assertEquals(roomType, response.get("roomType"));
        assertNotNull(response.get("inventoryEndpoint"));
        assertTrue((Boolean) response.get("available"));
        verify(bookingService, times(1)).isRoomAvailable(roomType);
    }

    @Test
    @DisplayName("Test downloadReport returns report path")
    void testDownloadReport_returnsReportPath() {
        // Arrange
        String month = "March";
        String expectedMessage = "Report generated successfully";
        when(bookingService.generateReport(month)).thenReturn(expectedMessage);

        // Act
        Map<String, Object> response = bookingController.downloadReport(month);

        // Assert
        assertNotNull(response);
        assertNotNull(response.get("reportPath"));
        assertTrue(response.get("reportPath").toString().contains(month));
        assertEquals(expectedMessage, response.get("message"));
        verify(bookingService, times(1)).generateReport(month);
    }
}
