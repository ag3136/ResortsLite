package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ReportService.
 * Tests all public methods, file operations, edge cases, and error scenarios.
 */
class ReportServiceTest {

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    @Test
    @DisplayName("generateMonthlyReport - should generate report with valid month and year")
    void testGenerateMonthlyReport_withValidInputs_generatesReport() {
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport - should handle empty month")
    void testGenerateMonthlyReport_withEmptyMonth_shouldProcess() {
        Map<String, Object> result = reportService.generateMonthlyReport("", "2024");
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("generateMonthlyReport - should handle empty year")
    void testGenerateMonthlyReport_withEmptyYear_shouldProcess() {
        Map<String, Object> result = reportService.generateMonthlyReport("03", "");
        assertNotNull(result);
        assertTrue(result.containsKey("status"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl - should build URL with report name")
    void testBuildReportDownloadUrl_withValidReportName_returnsUrl() {
        String url = reportService.buildReportDownloadUrl("report_03_2024.csv");
        assertNotNull(url);
        assertTrue(url.contains("report_03_2024.csv"));
        assertTrue(url.contains("http://"));
        assertTrue(url.contains("download"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl - should include correct domain")
    void testBuildReportDownloadUrl_shouldIncludeCorrectDomain() {
        String url = reportService.buildReportDownloadUrl("test_report.csv");
        assertTrue(url.contains("reports.resorts-internal.com"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl - should include port 8080")
    void testBuildReportDownloadUrl_shouldIncludePort() {
        String url = reportService.buildReportDownloadUrl("report.csv");
        assertTrue(url.contains(":8080"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl - should handle empty report name")
    void testBuildReportDownloadUrl_withEmptyReportName_returnsUrl() {
        String url = reportService.buildReportDownloadUrl("");
        assertNotNull(url);
        assertTrue(url.contains("http://"));
    }

    @Test
    @DisplayName("getSystemInfo - should return all system information")
    void testGetSystemInfo_returnsCompleteInfo() {
        Map<String, Object> info = reportService.getSystemInfo();
        assertNotNull(info);
        assertTrue(info.containsKey("reportPath"));
        assertTrue(info.containsKey("backupPath"));
        assertTrue(info.containsKey("serverPort"));
        assertTrue(info.containsKey("generatedAt"));
    }

    @Test
    @DisplayName("getSystemInfo - should include report path")
    void testGetSystemInfo_shouldIncludeReportPath() {
        Map<String, Object> info = reportService.getSystemInfo();
        String reportPath = (String) info.get("reportPath");
        assertNotNull(reportPath);
        assertTrue(reportPath.contains("/var/legacy/reports/"));
    }

    @Test
    @DisplayName("getSystemInfo - should include backup path")
    void testGetSystemInfo_shouldIncludeBackupPath() {
        Map<String, Object> info = reportService.getSystemInfo();
        String backupPath = (String) info.get("backupPath");
        assertNotNull(backupPath);
        assertTrue(backupPath.contains("ResortBackups"));
    }

    @Test
    @DisplayName("getSystemInfo - should include server port 8080")
    void testGetSystemInfo_shouldIncludeServerPort() {
        Map<String, Object> info = reportService.getSystemInfo();
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    @DisplayName("getSystemInfo - should include timestamp")
    void testGetSystemInfo_shouldIncludeTimestamp() {
        Map<String, Object> info = reportService.getSystemInfo();
        String timestamp = (String) info.get("generatedAt");
        assertNotNull(timestamp);
        assertTrue(timestamp.length() > 0);
    }

    @Test
    @DisplayName("getSystemInfo - should generate valid timestamp format")
    void testGetSystemInfo_shouldGenerateValidTimestamp() {
        Map<String, Object> info = reportService.getSystemInfo();
        String timestamp = (String) info.get("generatedAt");
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("buildReportDownloadUrl - should use HTTP protocol")
    void testBuildReportDownloadUrl_shouldUseHttpProtocol() {
        String url = reportService.buildReportDownloadUrl("report.csv");
        assertTrue(url.startsWith("http://"));
        assertFalse(url.startsWith("https://"));
    }
}
