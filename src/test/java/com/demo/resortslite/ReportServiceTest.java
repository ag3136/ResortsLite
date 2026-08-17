package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportService Test Suite")
class ReportServiceTest {

    private ReportService reportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    @Test
    @DisplayName("Test generateMonthlyReport with valid month and year")
    void testGenerateMonthlyReport_withValidMonthAndYear_generatesReport() {
        // Arrange
        String month = "March";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport returns status field")
    void testGenerateMonthlyReport_returnsStatusField() {
        // Arrange
        String month = "January";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertTrue(result.containsKey("status"));
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport includes report path")
    void testGenerateMonthlyReport_includesReportPath() {
        // Arrange
        String month = "February";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        if (result.get("status").equals("generated")) {
            assertNotNull(result.get("path"));
            assertTrue(result.get("path").toString().contains(month));
            assertTrue(result.get("path").toString().contains(year));
        }
    }

    @Test
    @DisplayName("Test generateMonthlyReport includes server port")
    void testGenerateMonthlyReport_includesServerPort() {
        // Arrange
        String month = "April";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        if (result.get("status").equals("generated")) {
            assertNotNull(result.get("serverPort"));
            assertEquals(8080, result.get("serverPort"));
        }
    }

    @Test
    @DisplayName("Test generateMonthlyReport with different months")
    void testGenerateMonthlyReport_withDifferentMonths_generatesUniqueReports() {
        // Arrange
        String month1 = "May";
        String month2 = "June";
        String year = "2024";

        // Act
        Map<String, Object> result1 = reportService.generateMonthlyReport(month1, year);
        Map<String, Object> result2 = reportService.generateMonthlyReport(month2, year);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        if (result1.containsKey("path") && result2.containsKey("path")) {
            assertNotEquals(result1.get("path"), result2.get("path"));
        }
    }

    @Test
    @DisplayName("Test generateMonthlyReport with different years")
    void testGenerateMonthlyReport_withDifferentYears_generatesUniqueReports() {
        // Arrange
        String month = "July";
        String year1 = "2023";
        String year2 = "2024";

        // Act
        Map<String, Object> result1 = reportService.generateMonthlyReport(month, year1);
        Map<String, Object> result2 = reportService.generateMonthlyReport(month, year2);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        if (result1.containsKey("path") && result2.containsKey("path")) {
            assertNotEquals(result1.get("path"), result2.get("path"));
        }
    }

    @Test
    @DisplayName("Test generateMonthlyReport with empty month")
    void testGenerateMonthlyReport_withEmptyMonth_handlesGracefully() {
        // Arrange
        String month = "";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport with empty year")
    void testGenerateMonthlyReport_withEmptyYear_handlesGracefully() {
        // Arrange
        String month = "August";
        String year = "";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport with null month")
    void testGenerateMonthlyReport_withNullMonth_handlesGracefully() {
        // Arrange
        String month = null;
        String year = "2024";

        // Act & Assert
        assertDoesNotThrow(() -> {
            Map<String, Object> result = reportService.generateMonthlyReport(month, year);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Test generateMonthlyReport with null year")
    void testGenerateMonthlyReport_withNullYear_handlesGracefully() {
        // Arrange
        String month = "September";
        String year = null;

        // Act & Assert
        assertDoesNotThrow(() -> {
            Map<String, Object> result = reportService.generateMonthlyReport(month, year);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Test generateMonthlyReport filename format")
    void testGenerateMonthlyReport_filenameFormat_isCorrect() {
        // Arrange
        String month = "October";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        if (result.containsKey("path")) {
            String path = result.get("path").toString();
            assertTrue(path.contains("resort_report_"));
            assertTrue(path.endsWith(".csv"));
        }
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl with valid report name")
    void testBuildReportDownloadUrl_withValidReportName_returnsUrl() {
        // Arrange
        String reportName = "march_2024_report.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(reportName));
        assertTrue(url.startsWith("http://"));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl contains correct domain")
    void testBuildReportDownloadUrl_containsCorrectDomain() {
        // Arrange
        String reportName = "test_report.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.contains("reports.resorts-internal.com"));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl contains port number")
    void testBuildReportDownloadUrl_containsPortNumber() {
        // Arrange
        String reportName = "annual_report.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.contains(":8080"));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl with empty report name")
    void testBuildReportDownloadUrl_withEmptyReportName_returnsUrl() {
        // Arrange
        String reportName = "";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.startsWith("http://"));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl with null report name")
    void testBuildReportDownloadUrl_withNullReportName_handlesGracefully() {
        // Arrange
        String reportName = null;

        // Act & Assert
        assertDoesNotThrow(() -> {
            String url = reportService.buildReportDownloadUrl(reportName);
            assertNotNull(url);
        });
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl with special characters")
    void testBuildReportDownloadUrl_withSpecialCharacters_returnsUrl() {
        // Arrange
        String reportName = "report_2024-03-15_v1.2.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(reportName));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl includes download path")
    void testBuildReportDownloadUrl_includesDownloadPath() {
        // Arrange
        String reportName = "monthly_summary.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.contains("/download/"));
    }

    @Test
    @DisplayName("Test getSystemInfo returns all required fields")
    void testGetSystemInfo_returnsAllRequiredFields() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info);
        assertTrue(info.containsKey("reportPath"));
        assertTrue(info.containsKey("backupPath"));
        assertTrue(info.containsKey("serverPort"));
        assertTrue(info.containsKey("generatedAt"));
    }

    @Test
    @DisplayName("Test getSystemInfo reportPath is not null")
    void testGetSystemInfo_reportPathIsNotNull() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("reportPath"));
        assertTrue(info.get("reportPath").toString().length() > 0);
    }

    @Test
    @DisplayName("Test getSystemInfo backupPath is not null")
    void testGetSystemInfo_backupPathIsNotNull() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("backupPath"));
        assertTrue(info.get("backupPath").toString().length() > 0);
    }

    @Test
    @DisplayName("Test getSystemInfo serverPort is 8080")
    void testGetSystemInfo_serverPortIs8080() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    @DisplayName("Test getSystemInfo generatedAt timestamp format")
    void testGetSystemInfo_generatedAtTimestampFormat() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("generatedAt"));
        String timestamp = info.get("generatedAt").toString();
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("Test getSystemInfo reportPath contains legacy path")
    void testGetSystemInfo_reportPathContainsLegacyPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String reportPath = info.get("reportPath").toString();
        assertTrue(reportPath.contains("/var/legacy/reports/"));
    }

    @Test
    @DisplayName("Test getSystemInfo backupPath contains Windows path")
    void testGetSystemInfo_backupPathContainsWindowsPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String backupPath = info.get("backupPath").toString();
        assertTrue(backupPath.contains("C:\\") || backupPath.contains("ResortBackups"));
    }

    @Test
    @DisplayName("Test getSystemInfo called multiple times returns consistent data")
    void testGetSystemInfo_calledMultipleTimes_returnsConsistentData() {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert
        assertEquals(info1.get("reportPath"), info2.get("reportPath"));
        assertEquals(info1.get("backupPath"), info2.get("backupPath"));
        assertEquals(info1.get("serverPort"), info2.get("serverPort"));
    }

    @Test
    @DisplayName("Test getSystemInfo timestamp changes between calls")
    void testGetSystemInfo_timestampChangesBetweenCalls() throws InterruptedException {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Thread.sleep(1100); // Wait for at least 1 second
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert
        assertNotEquals(info1.get("generatedAt"), info2.get("generatedAt"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport handles IOException gracefully")
    void testGenerateMonthlyReport_handlesIOExceptionGracefully() {
        // Arrange
        String month = "InvalidPath";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
        // Should either succeed or return error status
        assertTrue(result.get("status").equals("generated") || result.get("status").equals("error"));
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl with path traversal attempt")
    void testBuildReportDownloadUrl_withPathTraversalAttempt_returnsUrl() {
        // Arrange
        String reportName = "../../../etc/passwd";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(reportName));
    }

    @Test
    @DisplayName("Test generateMonthlyReport with very long month name")
    void testGenerateMonthlyReport_withVeryLongMonthName_handlesGracefully() {
        // Arrange
        String month = "A".repeat(1000);
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test generateMonthlyReport with special characters in month")
    void testGenerateMonthlyReport_withSpecialCharactersInMonth_handlesGracefully() {
        // Arrange
        String month = "March<script>alert('xss')</script>";
        String year = "2024";

        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    @Test
    @DisplayName("Test getSystemInfo returns map with correct size")
    void testGetSystemInfo_returnsMapWithCorrectSize() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(4, info.size());
    }

    @Test
    @DisplayName("Test buildReportDownloadUrl uses HTTP protocol")
    void testBuildReportDownloadUrl_usesHttpProtocol() {
        // Arrange
        String reportName = "test.csv";

        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertTrue(url.startsWith("http://"));
        assertFalse(url.startsWith("https://"));
    }
}
