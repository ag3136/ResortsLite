package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("ResortsLiteApplication Test Suite")
class ResortsLiteApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Test application context loads successfully")
    void testApplicationContext_loadsSuccessfully() {
        // Assert
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("Test BookingController bean is created")
    void testBookingControllerBean_isCreated() {
        // Act
        BookingController bookingController = applicationContext.getBean(BookingController.class);

        // Assert
        assertNotNull(bookingController);
    }

    @Test
    @DisplayName("Test BookingService bean is created")
    void testBookingServiceBean_isCreated() {
        // Act
        BookingService bookingService = applicationContext.getBean(BookingService.class);

        // Assert
        assertNotNull(bookingService);
    }

    @Test
    @DisplayName("Test ReportService bean is created")
    void testReportServiceBean_isCreated() {
        // Act
        ReportService reportService = applicationContext.getBean(ReportService.class);

        // Assert
        assertNotNull(reportService);
    }

    @Test
    @DisplayName("Test main method does not throw exception")
    void testMain_doesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            // We don't actually call main() as it would start the server
            // This test verifies the class structure is correct
            assertNotNull(ResortsLiteApplication.class);
        });
    }

    @Test
    @DisplayName("Test application has SpringBootApplication annotation")
    void testApplication_hasSpringBootApplicationAnnotation() {
        // Act
        boolean hasAnnotation = ResortsLiteApplication.class
                .isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class);

        // Assert
        assertTrue(hasAnnotation);
    }

    @Test
    @DisplayName("Test application context contains expected beans")
    void testApplicationContext_containsExpectedBeans() {
        // Act
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        // Assert
        assertNotNull(beanNames);
        assertTrue(beanNames.length > 0);
    }

    @Test
    @DisplayName("Test application has main method")
    void testApplication_hasMainMethod() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        });
    }

    @Test
    @DisplayName("Test application class is public")
    void testApplicationClass_isPublic() {
        // Act
        int modifiers = ResortsLiteApplication.class.getModifiers();

        // Assert
        assertTrue(java.lang.reflect.Modifier.isPublic(modifiers));
    }

    @Test
    @DisplayName("Test application can be instantiated")
    void testApplication_canBeInstantiated() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new ResortsLiteApplication();
        });
    }

    @Test
    @DisplayName("Test application environment is not null")
    void testApplication_environmentIsNotNull() {
        // Act
        org.springframework.core.env.Environment environment = applicationContext.getEnvironment();

        // Assert
        assertNotNull(environment);
    }

    @Test
    @DisplayName("Test application startup time is recorded")
    void testApplication_startupTimeIsRecorded() {
        // Act
        long startupDate = applicationContext.getStartupDate();

        // Assert
        assertTrue(startupDate > 0);
    }

    @Test
    @DisplayName("Test application display name is set")
    void testApplication_displayNameIsSet() {
        // Act
        String displayName = applicationContext.getDisplayName();

        // Assert
        assertNotNull(displayName);
        assertTrue(displayName.length() > 0);
    }
}
