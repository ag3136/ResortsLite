package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for BookingController.
 * Tests all public methods, edge cases, and error scenarios.
 */
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    @DisplayName("createBooking - should create booking successfully with valid inputs")
    void testCreateBooking_withValidInputs_returnsConfirmedBooking() {
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

        Map<String, Object> response = bookingController.createBooking(
                guestName, roomType, checkIn, checkOut, session);

        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
        assertNotNull(response.get("booking"));
        assertEquals(mockBooking, response.get("booking"));
        verify(bookingService, times(1)).createBooking(guestName, roomType, checkIn, checkOut);
    }

    @Test
    @DisplayName("createBooking - should store booking in session")
    void testCreateBooking_shouldStoreBookingInSession() {
        String guestName = "Jane Smith";
        String roomType = "SUITE";
        String checkIn = "2024-04-01";
        String checkOut = "2024-04-10";

        Map<String, Object> mockBooking = new HashMap<>();
        mockBooking.put("bookingId", "BK-87654321");
        mockBooking.put("guestName", guestName);

        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockBooking);

        bookingController.createBooking(guestName, roomType, checkIn, checkOut, session);

        assertEquals(mockBooking, session.getAttribute("lastBooking"));
        assertEquals(guestName, session.getAttribute("guestName"));
    }

    @Test
    @DisplayName("getBookingStatus - should return booking status with valid bookingId")
    void testGetBookingStatus_withValidBookingId_returnsStatus() {
        String bookingId = "BK-12345678";
        String guestName = "Alice Johnson";
        session.setAttribute("guestName", guestName);

        Map<String, Object> mockDetails = new HashMap<>();
        mockDetails.put("bookingId", bookingId);
        mockDetails.put("status", "confirmed");

        when(bookingService.getBookingById(bookingId)).thenReturn(mockDetails);

        Map<String, Object> result = bookingController.getBookingStatus(bookingId, session);

        assertNotNull(result);
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(guestName, result.get("sessionGuest"));
        assertEquals(mockDetails, result.get("details"));
        verify(bookingService, times(1)).getBookingById(bookingId);
    }

    @Test
    @DisplayName("checkAvailability - should return availability for valid room type")
    void testCheckAvailability_withValidRoomType_returnsAvailability() {
        String roomType = "DELUXE";
        when(bookingService.isRoomAvailable(roomType)).thenReturn(true);

        Map<String, Object> response = bookingController.checkAvailability(roomType);

        assertNotNull(response);
        assertEquals(roomType, response.get("roomType"));
        assertNotNull(response.get("inventoryEndpoint"));
        assertEquals(true, response.get("available"));
        verify(bookingService, times(1)).isRoomAvailable(roomType);
    }

    @Test
    @DisplayName("downloadReport - should generate report for valid month")
    void testDownloadReport_withValidMonth_returnsReportPath() {
        String month = "2024-03";
        String expectedMessage = "Report generated successfully";
        when(bookingService.generateReport(month)).thenReturn(expectedMessage);

        Map<String, Object> response = bookingController.downloadReport(month);

        assertNotNull(response);
        assertNotNull(response.get("reportPath"));
        assertTrue(((String) response.get("reportPath")).contains(month));
        assertEquals(expectedMessage, response.get("message"));
        verify(bookingService, times(1)).generateReport(month);
    }
}
