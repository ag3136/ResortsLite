package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private HttpSession httpSession;

    @InjectMocks
    private BookingController bookingController;

    private Map<String, Object> sampleBooking;

    @BeforeEach
    void setUp() {
        sampleBooking = new HashMap<>();
        sampleBooking.put("bookingId", "BK-ABCD1234");
        sampleBooking.put("guestName", "Alice");
        sampleBooking.put("roomType", "SUITE");
        sampleBooking.put("checkIn", "2024-06-01");
        sampleBooking.put("checkOut", "2024-06-05");
        sampleBooking.put("confirmationCode", "abc123hash");
    }

    // -----------------------------------------------------------------------
    // createBooking tests
    // -----------------------------------------------------------------------

    @Test
    void createBooking_withValidParams_returnsNonNullResponse() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertNotNull(response);
    }

    @Test
    void createBooking_responseContainsStatusConfirmed() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertEquals("confirmed", response.get("status"));
    }

    @Test
    void createBooking_responseContainsBookingKey() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertTrue(response.containsKey("booking"));
    }

    @Test
    void createBooking_bookingInResponseMatchesServiceResult() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertEquals(sampleBooking, response.get("booking"));
    }

    @Test
    void createBooking_setsLastBookingInSession() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        verify(httpSession, times(1)).setAttribute(eq("lastBooking"), eq(sampleBooking));
    }

    @Test
    void createBooking_setsGuestNameInSession() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);

        // Act
        bookingController.createBooking(
                "Alice", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        verify(httpSession, times(1)).setAttribute(eq("guestName"), eq("Alice"));
    }

    @Test
    void createBooking_callsBookingServiceWithCorrectParams() {
        // Arrange
        when(bookingService.createBooking("Bob", "DELUXE", "2024-07-01", "2024-07-05"))
                .thenReturn(sampleBooking);

        // Act
        bookingController.createBooking(
                "Bob", "DELUXE", "2024-07-01", "2024-07-05", httpSession);

        // Assert
        verify(bookingService, times(1))
                .createBooking("Bob", "DELUXE", "2024-07-01", "2024-07-05");
    }

    // -----------------------------------------------------------------------
    // getBookingStatus tests
    // -----------------------------------------------------------------------

    @Test
    void getBookingStatus_returnsNonNullResult() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById("BK-ABCD1234")).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertNotNull(result);
    }

    @Test
    void getBookingStatus_resultContainsBookingId() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById("BK-ABCD1234")).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertEquals("BK-ABCD1234", result.get("bookingId"));
    }

    @Test
    void getBookingStatus_resultContainsSessionGuest() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertEquals("Alice", result.get("sessionGuest"));
    }

    @Test
    void getBookingStatus_resultContainsDetailsKey() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertTrue(result.containsKey("details"));
    }

    @Test
    void getBookingStatus_detailsMatchServiceResult() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById("BK-ABCD1234")).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertEquals(sampleBooking, result.get("details"));
    }

    @Test
    void getBookingStatus_whenSessionGuestIsNull_sessionGuestIsNull() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn(null);
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertNull(result.get("sessionGuest"));
    }

    @Test
    void getBookingStatus_callsGetBookingByIdWithCorrectId() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById("BK-XYZ99")).thenReturn(sampleBooking);

        // Act
        bookingController.getBookingStatus("BK-XYZ99", httpSession);

        // Assert
        verify(bookingService, times(1)).getBookingById("BK-XYZ99");
    }

    // -----------------------------------------------------------------------
    // checkAvailability tests
    // -----------------------------------------------------------------------

    @Test
    void checkAvailability_returnsNonNullResponse() {
        // Arrange
        when(bookingService.isRoomAvailable("SUITE")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("SUITE");

        // Assert
        assertNotNull(response);
    }

    @Test
    void checkAvailability_responseContainsRoomType() {
        // Arrange
        when(bookingService.isRoomAvailable("SUITE")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("SUITE");

        // Assert
        assertEquals("SUITE", response.get("roomType"));
    }

    @Test
    void checkAvailability_responseContainsInventoryEndpoint() {
        // Arrange
        when(bookingService.isRoomAvailable("DELUXE")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("DELUXE");

        // Assert
        assertTrue(response.containsKey("inventoryEndpoint"));
        assertNotNull(response.get("inventoryEndpoint"));
    }

    @Test
    void checkAvailability_responseContainsAvailableKey() {
        // Arrange
        when(bookingService.isRoomAvailable("STANDARD")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("STANDARD");

        // Assert
        assertTrue(response.containsKey("available"));
    }

    @Test
    void checkAvailability_whenRoomAvailable_availableIsTrue() {
        // Arrange
        when(bookingService.isRoomAvailable("VILLA")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("VILLA");

        // Assert
        assertEquals(true, response.get("available"));
    }

    @Test
    void checkAvailability_whenRoomNotAvailable_availableIsFalse() {
        // Arrange
        when(bookingService.isRoomAvailable("PENTHOUSE")).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("PENTHOUSE");

        // Assert
        assertEquals(false, response.get("available"));
    }

    @Test
    void checkAvailability_inventoryEndpointContainsInternalHost() {
        // Arrange
        when(bookingService.isRoomAvailable(anyString())).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("SUITE");

        // Assert
        String endpoint = (String) response.get("inventoryEndpoint");
        assertTrue(endpoint.contains("inventory-service.internal"));
    }

    // -----------------------------------------------------------------------
    // downloadReport tests
    // -----------------------------------------------------------------------

    @Test
    void downloadReport_returnsNonNullResponse() {
        // Arrange
        when(bookingService.generateReport("2024-03")).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-03");

        // Assert
        assertNotNull(response);
    }

    @Test
    void downloadReport_responseContainsReportPath() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-03");

        // Assert
        assertTrue(response.containsKey("reportPath"));
    }

    @Test
    void downloadReport_reportPathContainsMonth() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-03");

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.contains("2024-03"));
    }

    @Test
    void downloadReport_reportPathEndsWithPdf() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-03");

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.endsWith(".pdf"));
    }

    @Test
    void downloadReport_responseContainsMessageKey() {
        // Arrange
        when(bookingService.generateReport("2024-06")).thenReturn("Report triggered");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-06");

        // Assert
        assertTrue(response.containsKey("message"));
    }

    @Test
    void downloadReport_messageMatchesServiceResult() {
        // Arrange
        when(bookingService.generateReport("2024-06")).thenReturn("Report triggered for 2024-06");

        // Act
        Map<String, Object> response = bookingController.downloadReport("2024-06");

        // Assert
        assertEquals("Report triggered for 2024-06", response.get("message"));
    }

    @Test
    void downloadReport_callsGenerateReportWithCorrectMonth() {
        // Arrange
        when(bookingService.generateReport("2024-09")).thenReturn("Done");

        // Act
        bookingController.downloadReport("2024-09");

        // Assert
        verify(bookingService, times(1)).generateReport("2024-09");
    }
}
