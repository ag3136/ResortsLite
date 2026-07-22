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
        sampleBooking.put("guestName", "John Smith");
        sampleBooking.put("roomType", "SUITE");
        sampleBooking.put("checkIn", "2024-06-01");
        sampleBooking.put("checkOut", "2024-06-05");
        sampleBooking.put("confirmationCode", "abc123def456");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createBooking tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createBooking_withValidParams_returnsConfirmedStatus() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "John Smith", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertNotNull(response);
        assertEquals("confirmed", response.get("status"));
    }

    @Test
    void createBooking_withValidParams_returnsBookingInResponse() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "John Smith", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        assertNotNull(response.get("booking"));
        assertEquals(sampleBooking, response.get("booking"));
    }

    @Test
    void createBooking_storesBookingInSession() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        bookingController.createBooking(
                "John Smith", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        verify(httpSession, times(1)).setAttribute(eq("lastBooking"), eq(sampleBooking));
    }

    @Test
    void createBooking_storesGuestNameInSession() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        bookingController.createBooking(
                "John Smith", "SUITE", "2024-06-01", "2024-06-05", httpSession);

        // Assert
        verify(httpSession, times(1)).setAttribute(eq("guestName"), eq("John Smith"));
    }

    @Test
    void createBooking_callsBookingServiceWithCorrectParams() {
        // Arrange
        when(bookingService.createBooking("Alice", "DELUXE", "2024-07-01", "2024-07-03"))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        bookingController.createBooking("Alice", "DELUXE", "2024-07-01", "2024-07-03", httpSession);

        // Assert
        verify(bookingService, times(1))
                .createBooking("Alice", "DELUXE", "2024-07-01", "2024-07-03");
    }

    @Test
    void createBooking_responseContainsTwoKeys() {
        // Arrange
        when(bookingService.createBooking(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sampleBooking);
        doNothing().when(httpSession).setAttribute(anyString(), any());

        // Act
        Map<String, Object> response = bookingController.createBooking(
                "Bob", "STANDARD", "2024-08-01", "2024-08-02", httpSession);

        // Assert
        assertEquals(2, response.size());
        assertTrue(response.containsKey("status"));
        assertTrue(response.containsKey("booking"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getBookingStatus tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getBookingStatus_withValidBookingId_returnsResultMap() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("John Smith");
        when(bookingService.getBookingById("BK-ABCD1234")).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertNotNull(result);
        assertEquals("BK-ABCD1234", result.get("bookingId"));
    }

    @Test
    void getBookingStatus_returnsSessionGuestName() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("John Smith");
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertEquals("John Smith", result.get("sessionGuest"));
    }

    @Test
    void getBookingStatus_whenSessionGuestIsNull_returnsNullSessionGuest() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn(null);
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertNull(result.get("sessionGuest"));
    }

    @Test
    void getBookingStatus_returnsDetailsFromService() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Alice");
        when(bookingService.getBookingById("BK-XYZ99999")).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-XYZ99999", httpSession);

        // Assert
        assertEquals(sampleBooking, result.get("details"));
    }

    @Test
    void getBookingStatus_resultContainsThreeKeys() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Bob");
        when(bookingService.getBookingById(anyString())).thenReturn(sampleBooking);

        // Act
        Map<String, Object> result = bookingController.getBookingStatus("BK-ABCD1234", httpSession);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.containsKey("bookingId"));
        assertTrue(result.containsKey("sessionGuest"));
        assertTrue(result.containsKey("details"));
    }

    @Test
    void getBookingStatus_callsBookingServiceWithCorrectId() {
        // Arrange
        when(httpSession.getAttribute("guestName")).thenReturn("Carol");
        when(bookingService.getBookingById("BK-TEST0001")).thenReturn(sampleBooking);

        // Act
        bookingController.getBookingStatus("BK-TEST0001", httpSession);

        // Assert
        verify(bookingService, times(1)).getBookingById("BK-TEST0001");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkAvailability tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void checkAvailability_withValidRoomType_returnsResponseMap() {
        // Arrange
        when(bookingService.isRoomAvailable("SUITE")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("SUITE");

        // Assert
        assertNotNull(response);
    }

    @Test
    void checkAvailability_returnsRoomTypeInResponse() {
        // Arrange
        when(bookingService.isRoomAvailable("DELUXE")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("DELUXE");

        // Assert
        assertEquals("DELUXE", response.get("roomType"));
    }

    @Test
    void checkAvailability_whenRoomAvailable_returnsTrue() {
        // Arrange
        when(bookingService.isRoomAvailable("STANDARD")).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("STANDARD");

        // Assert
        assertTrue((Boolean) response.get("available"));
    }

    @Test
    void checkAvailability_whenRoomNotAvailable_returnsFalse() {
        // Arrange
        when(bookingService.isRoomAvailable("PENTHOUSE")).thenReturn(false);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("PENTHOUSE");

        // Assert
        assertFalse((Boolean) response.get("available"));
    }

    @Test
    void checkAvailability_responseContainsInventoryEndpoint() {
        // Arrange
        when(bookingService.isRoomAvailable(anyString())).thenReturn(true);

        // Act
        Map<String, Object> response = bookingController.checkAvailability("VILLA");

        // Assert
        assertNotNull(response.get("inventoryEndpoint"));
        assertTrue(((String) response.get("inventoryEndpoint")).contains("inventory-service"));
    }

    @Test
    void checkAvailability_callsBookingServiceIsRoomAvailable() {
        // Arrange
        when(bookingService.isRoomAvailable("SUITE")).thenReturn(true);

        // Act
        bookingController.checkAvailability("SUITE");

        // Assert
        verify(bookingService, times(1)).isRoomAvailable("SUITE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // downloadReport tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void downloadReport_withValidMonth_returnsResponseMap() {
        // Arrange
        when(bookingService.generateReport("March")).thenReturn("Report generated for March");

        // Act
        Map<String, Object> response = bookingController.downloadReport("March");

        // Assert
        assertNotNull(response);
    }

    @Test
    void downloadReport_responseContainsReportPath() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("June");

        // Assert
        assertNotNull(response.get("reportPath"));
        assertTrue(((String) response.get("reportPath")).contains("June"));
    }

    @Test
    void downloadReport_reportPathContainsMonthAndPdfExtension() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("December");

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.contains("December"));
        assertTrue(reportPath.endsWith(".pdf"));
    }

    @Test
    void downloadReport_responseContainsMessage() {
        // Arrange
        String expectedMessage = "Report generation triggered for: January";
        when(bookingService.generateReport("January")).thenReturn(expectedMessage);

        // Act
        Map<String, Object> response = bookingController.downloadReport("January");

        // Assert
        assertEquals(expectedMessage, response.get("message"));
    }

    @Test
    void downloadReport_callsBookingServiceGenerateReport() {
        // Arrange
        when(bookingService.generateReport("July")).thenReturn("Report for July");

        // Act
        bookingController.downloadReport("July");

        // Assert
        verify(bookingService, times(1)).generateReport("July");
    }

    @Test
    void downloadReport_reportPathStartsWithLegacyPath() {
        // Arrange
        when(bookingService.generateReport(anyString())).thenReturn("Report generated");

        // Act
        Map<String, Object> response = bookingController.downloadReport("August");

        // Assert
        String reportPath = (String) response.get("reportPath");
        assertTrue(reportPath.startsWith("/var/legacy/reports/"));
    }
}
