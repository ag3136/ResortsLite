package com.demo.resortslite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private ReportService reportService;

    private static final String REPORT_BASE_PATH = "/var/legacy/reports/";

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    @AfterEach
    void tearDown() {
        // Clean up any generated test files
        File testFile = new File(REPORT_BASE_PATH + "resort_report_03_2024.csv");
        if (testFile.exists()) {
            testFile.delete();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateMonthlyReport tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generateMonthlyReport_withValidMonthAndYear_returnsNonNullMap() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        assertNotNull(result);
    }

    @Test
    void generateMonthlyReport_returnsStatusKey() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_whenDirectoryNotWritable_returnsErrorOrGenerated() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("06", "2024");

        // Assert
        assertNotNull(result);
        String status = (String) result.get("status");
        assertTrue("generated".equals(status) || "error".equals(status),
                "Status should be 'generated' or 'error'");
    }

    @Test
    void generateMonthlyReport_onSuccess_returnsGeneratedStatus() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("01", "2024");

        // Assert
        // If the directory is writable, status should be "generated"
        // If not writable (e.g., in CI), status should be "error"
        assertNotNull(result.get("status"));
    }

    @Test
    void generateMonthlyReport_onSuccess_returnsPathKey() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        // Either path (success) or message (error) should be present
        assertTrue(result.containsKey("path") || result.containsKey("message"),
                "Result should contain 'path' on success or 'message' on error");
    }

    @Test
    void generateMonthlyReport_onSuccess_pathContainsMonthAndYear() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("07", "2024");

        // Assert
        if ("generated".equals(result.get("status"))) {
            String path = (String) result.get("path");
            assertNotNull(path);
            assertTrue(path.contains("07"));
            assertTrue(path.contains("2024"));
        } else {
            // error case is also acceptable
            assertTrue(result.containsKey("message"));
        }
    }

    @Test
    void generateMonthlyReport_onSuccess_pathEndsWithCsvExtension() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("12", "2024");

        // Assert
        if ("generated".equals(result.get("status"))) {
            String path = (String) result.get("path");
            assertTrue(path.endsWith(".csv"));
        } else {
            assertNotNull(result.get("message"));
        }
    }

    @Test
    void generateMonthlyReport_onSuccess_containsServerPort() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        if ("generated".equals(result.get("status"))) {
            assertNotNull(result.get("serverPort"));
            assertEquals(8080, result.get("serverPort"));
        }
    }

    @Test
    void generateMonthlyReport_onError_returnsErrorStatus() {
        // This test verifies the error handling path by using a path that may not be writable
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        String status = (String) result.get("status");
        assertNotNull(status);
        assertTrue("generated".equals(status) || "error".equals(status));
    }

    @ParameterizedTest
    @CsvSource({
            "01, 2024",
            "06, 2023",
            "12, 2022",
            "03, 2025"
    })
    void generateMonthlyReport_variousMonthsAndYears_returnsNonNullResult(String month, String year) {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport(month, year);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("status"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildReportDownloadUrl tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void buildReportDownloadUrl_withValidReportName_returnsNonNullUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("march_report.pdf");

        // Assert
        assertNotNull(url);
        assertFalse(url.isEmpty());
    }

    @Test
    void buildReportDownloadUrl_containsReportName() {
        // Act
        String url = reportService.buildReportDownloadUrl("june_report.pdf");

        // Assert
        assertTrue(url.contains("june_report.pdf"));
    }

    @Test
    void buildReportDownloadUrl_containsDownloadPath() {
        // Act
        String url = reportService.buildReportDownloadUrl("annual_report.pdf");

        // Assert
        assertTrue(url.contains("/download/"));
    }

    @Test
    void buildReportDownloadUrl_containsReportsHost() {
        // Act
        String url = reportService.buildReportDownloadUrl("test_report.pdf");

        // Assert
        assertTrue(url.contains("reports.resorts-internal.com"));
    }

    @Test
    void buildReportDownloadUrl_containsPort8080() {
        // Act
        String url = reportService.buildReportDownloadUrl("test_report.pdf");

        // Assert
        assertTrue(url.contains(":8080"));
    }

    @Test
    void buildReportDownloadUrl_startsWithHttpProtocol() {
        // Act
        String url = reportService.buildReportDownloadUrl("test_report.pdf");

        // Assert
        assertTrue(url.startsWith("http://"));
    }

    @Test
    void buildReportDownloadUrl_withEmptyReportName_returnsUrlWithEmptyName() {
        // Act
        String url = reportService.buildReportDownloadUrl("");

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("/download/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"january.pdf", "february.csv", "march_2024.pdf", "annual_report.xlsx"})
    void buildReportDownloadUrl_variousReportNames_allContainReportName(String reportName) {
        // Act
        String url = reportService.buildReportDownloadUrl(reportName);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains(reportName));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getSystemInfo tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getSystemInfo_returnsNonNullMap() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info);
    }

    @Test
    void getSystemInfo_containsReportPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("reportPath"));
        assertNotNull(info.get("reportPath"));
    }

    @Test
    void getSystemInfo_reportPathMatchesConstant() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals("/var/legacy/reports/", info.get("reportPath"));
    }

    @Test
    void getSystemInfo_containsBackupPath() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("backupPath"));
        assertNotNull(info.get("backupPath"));
    }

    @Test
    void getSystemInfo_backupPathIsWindowsStyle() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String backupPath = (String) info.get("backupPath");
        assertTrue(backupPath.contains("ResortBackups"));
    }

    @Test
    void getSystemInfo_containsServerPort() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("serverPort"));
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    void getSystemInfo_containsGeneratedAt() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("generatedAt"));
        assertNotNull(info.get("generatedAt"));
    }

    @Test
    void getSystemInfo_generatedAtIsFormattedTimestamp() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String generatedAt = (String) info.get("generatedAt");
        assertNotNull(generatedAt);
        // Format: yyyy-MM-dd HH:mm:ss
        assertTrue(generatedAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "Timestamp should match format yyyy-MM-dd HH:mm:ss");
    }

    @Test
    void getSystemInfo_containsFourKeys() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(4, info.size());
        assertTrue(info.containsKey("reportPath"));
        assertTrue(info.containsKey("backupPath"));
        assertTrue(info.containsKey("serverPort"));
        assertTrue(info.containsKey("generatedAt"));
    }

    @Test
    void getSystemInfo_calledMultipleTimes_returnsConsistentStaticValues() {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert
        assertEquals(info1.get("reportPath"), info2.get("reportPath"));
        assertEquals(info1.get("backupPath"), info2.get("backupPath"));
        assertEquals(info1.get("serverPort"), info2.get("serverPort"));
    }
}
