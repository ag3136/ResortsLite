package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ResortsLiteApplicationTest {

    /**
     * Verifies that the Spring application context loads successfully.
     * This is the primary smoke test for the application.
     */
    @Test
    void contextLoads() {
        // If the context fails to load, this test will fail automatically
        // No explicit assertion needed — Spring Boot test infrastructure handles it
        assertTrue(true, "Application context should load without errors");
    }

    /**
     * Verifies that the main method can be invoked without throwing exceptions.
     */
    @Test
    void main_doesNotThrowException() {
        // Arrange / Act / Assert
        assertDoesNotThrow(() ->
                ResortsLiteApplication.main(new String[]{}),
                "main() should not throw any exception"
        );
    }
}
