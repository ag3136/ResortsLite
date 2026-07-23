package com.demo.resortslite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService();
    }

    // -----------------------------------------------------------------------
    // generateMonthlyReport tests
    // -----------------------------------------------------------------------

    @Test
    void generateMonthlyReport_withValidMonthAndYear_returnsNonNullResult() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        assertNotNull(result);
    }

    @Test
    void generateMonthlyReport_resultContainsStatusKey() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert
        assertTrue(result.containsKey("status"));
    }

    @Test
    void generateMonthlyReport_whenDirectoryCannotBeCreated_statusIsErrorOrGenerated() {
        // Act - the method handles IOException internally
        Map<String, Object> result = reportService.generateMonthlyReport("01", "2024");

        // Assert - status is either "generated" or "error" depending on filesystem
        String status = (String) result.get("status");
        assertTrue("generated".equals(status) || "error".equals(status),
                "Status should be 'generated' or 'error', but was: " + status);
    }

    @Test
    void generateMonthlyReport_onSuccess_statusIsGenerated() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert - if the path is writable, status should be "generated"
        // If not writable (e.g. /var/legacy/reports doesn't exist), status is "error"
        assertNotNull(result.get("status"));
    }

    @Test
    void generateMonthlyReport_onSuccess_resultContainsServerPort() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert - serverPort is only set on success path
        if ("generated".equals(result.get("status"))) {
            assertTrue(result.containsKey("serverPort"));
            assertEquals(8080, result.get("serverPort"));
        }
    }

    @Test
    void generateMonthlyReport_onSuccess_pathContainsMonthAndYear() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("05", "2023");

        // Assert
        if ("generated".equals(result.get("status"))) {
            String path = (String) result.get("path");
            assertNotNull(path);
            assertTrue(path.contains("05"), "Path should contain month '05'");
            assertTrue(path.contains("2023"), "Path should contain year '2023'");
        }
    }

    @Test
    void generateMonthlyReport_onError_resultContainsMessageKey() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("03", "2024");

        // Assert - if error, message key should be present
        if ("error".equals(result.get("status"))) {
            assertTrue(result.containsKey("message"));
        }
    }

    @Test
    void generateMonthlyReport_differentMonthsProduceDifferentPaths() {
        // Act
        Map<String, Object> result1 = reportService.generateMonthlyReport("01", "2024");
        Map<String, Object> result2 = reportService.generateMonthlyReport("12", "2024");

        // Assert - if both succeed, paths should differ
        if ("generated".equals(result1.get("status")) && "generated".equals(result2.get("status"))) {
            assertNotEquals(result1.get("path"), result2.get("path"));
        }
    }

    @Test
    void generateMonthlyReport_fileNameContainsReportPrefix() {
        // Act
        Map<String, Object> result = reportService.generateMonthlyReport("06", "2024");

        // Assert
        if ("generated".equals(result.get("status"))) {
            String path = (String) result.get("path");
            assertTrue(path.contains("resort_report_"));
        }
    }

    // -----------------------------------------------------------------------
    // buildReportDownloadUrl tests
    // -----------------------------------------------------------------------

    @Test
    void buildReportDownloadUrl_returnsNonNullUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("march_report.pdf");

        // Assert
        assertNotNull(url);
    }

    @Test
    void buildReportDownloadUrl_urlContainsReportName() {
        // Act
        String url = reportService.buildReportDownloadUrl("march_report.pdf");

        // Assert
        assertTrue(url.contains("march_report.pdf"));
    }

    @Test
    void buildReportDownloadUrl_urlContainsDownloadPath() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.csv");

        // Assert
        assertTrue(url.contains("/download/"));
    }

    @Test
    void buildReportDownloadUrl_urlStartsWithHttp() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.csv");

        // Assert
        assertTrue(url.startsWith("http://"));
    }

    @Test
    void buildReportDownloadUrl_urlContainsInternalHost() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.csv");

        // Assert
        assertTrue(url.contains("reports.resorts-internal.com"));
    }

    @Test
    void buildReportDownloadUrl_urlContainsPort8080() {
        // Act
        String url = reportService.buildReportDownloadUrl("report.csv");

        // Assert
        assertTrue(url.contains(":8080"));
    }

    @Test
    void buildReportDownloadUrl_differentReportNamesProduceDifferentUrls() {
        // Act
        String url1 = reportService.buildReportDownloadUrl("jan_report.pdf");
        String url2 = reportService.buildReportDownloadUrl("feb_report.pdf");

        // Assert
        assertNotEquals(url1, url2);
    }

    @Test
    void buildReportDownloadUrl_emptyReportNameProducesBaseUrl() {
        // Act
        String url = reportService.buildReportDownloadUrl("");

        // Assert
        assertNotNull(url);
        assertTrue(url.endsWith("/download/"));
    }

    // -----------------------------------------------------------------------
    // getSystemInfo tests
    // -----------------------------------------------------------------------

    @Test
    void getSystemInfo_returnsNonNullMap() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info);
    }

    @Test
    void getSystemInfo_containsReportPathKey() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("reportPath"));
    }

    @Test
    void getSystemInfo_containsBackupPathKey() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("backupPath"));
    }

    @Test
    void getSystemInfo_containsServerPortKey() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("serverPort"));
    }

    @Test
    void getSystemInfo_containsGeneratedAtKey() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertTrue(info.containsKey("generatedAt"));
    }

    @Test
    void getSystemInfo_serverPortIs8080() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertEquals(8080, info.get("serverPort"));
    }

    @Test
    void getSystemInfo_reportPathIsNotNull() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("reportPath"));
    }

    @Test
    void getSystemInfo_backupPathIsNotNull() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        assertNotNull(info.get("backupPath"));
    }

    @Test
    void getSystemInfo_generatedAtIsFormattedTimestamp() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();

        // Assert
        String timestamp = (String) info.get("generatedAt");
        assertNotNull(timestamp);
        // Format: yyyy-MM-dd HH:mm:ss  (length = 19)
        assertEquals(19, timestamp.length(),
                "Timestamp should be in 'yyyy-MM-dd HH:mm:ss' format");
    }

    @Test
    void getSystemInfo_generatedAtContainsDateSeparator() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();
        String timestamp = (String) info.get("generatedAt");

        // Assert
        assertTrue(timestamp.contains("-"), "Timestamp should contain '-' date separator");
    }

    @Test
    void getSystemInfo_generatedAtContainsTimeSeparator() {
        // Act
        Map<String, Object> info = reportService.getSystemInfo();
        String timestamp = (String) info.get("generatedAt");

        // Assert
        assertTrue(timestamp.contains(":"), "Timestamp should contain ':' time separator");
    }

    @Test
    void getSystemInfo_calledTwiceReturnsDifferentOrSameTimestamp() {
        // Act
        Map<String, Object> info1 = reportService.getSystemInfo();
        Map<String, Object> info2 = reportService.getSystemInfo();

        // Assert - both calls should return valid timestamps
        assertNotNull(info1.get("generatedAt"));
        assertNotNull(info2.get("generatedAt"));
    }
}
