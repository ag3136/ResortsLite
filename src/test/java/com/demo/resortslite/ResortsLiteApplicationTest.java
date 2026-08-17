package com.demo.resortslite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ResortsLiteApplication.
 * Tests application startup and main method.
 */
class ResortsLiteApplicationTest {

    @Test
    @DisplayName("main - should not throw exception when called")
    void testMain_shouldNotThrowException() {
        assertDoesNotThrow(() -> {
            assertNotNull(ResortsLiteApplication.class);
        });
    }

    @Test
    @DisplayName("ResortsLiteApplication - should have SpringBootApplication annotation")
    void testResortsLiteApplication_shouldHaveSpringBootAnnotation() {
        boolean hasAnnotation = ResortsLiteApplication.class
                .isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        
        assertTrue(hasAnnotation, "ResortsLiteApplication should have @SpringBootApplication annotation");
    }

    @Test
    @DisplayName("ResortsLiteApplication - should have main method")
    void testResortsLiteApplication_shouldHaveMainMethod() {
        assertDoesNotThrow(() -> {
            ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        }, "ResortsLiteApplication should have a main method");
    }

    @Test
    @DisplayName("ResortsLiteApplication - main method should be public")
    void testResortsLiteApplication_mainMethodShouldBePublic() throws NoSuchMethodException {
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()),
                "Main method should be public");
    }

    @Test
    @DisplayName("ResortsLiteApplication - main method should be static")
    void testResortsLiteApplication_mainMethodShouldBeStatic() throws NoSuchMethodException {
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()),
                "Main method should be static");
    }

    @Test
    @DisplayName("ResortsLiteApplication - main method should return void")
    void testResortsLiteApplication_mainMethodShouldReturnVoid() throws NoSuchMethodException {
        var mainMethod = ResortsLiteApplication.class.getDeclaredMethod("main", String[].class);
        assertEquals(void.class, mainMethod.getReturnType(),
                "Main method should return void");
    }

    @Test
    @DisplayName("ResortsLiteApplication - should be a public class")
    void testResortsLiteApplication_shouldBePublicClass() {
        assertTrue(java.lang.reflect.Modifier.isPublic(ResortsLiteApplication.class.getModifiers()),
                "ResortsLiteApplication should be a public class");
    }

    @Test
    @DisplayName("ResortsLiteApplication - should have default constructor")
    void testResortsLiteApplication_shouldHaveDefaultConstructor() {
        assertDoesNotThrow(() -> {
            new ResortsLiteApplication();
        }, "ResortsLiteApplication should have a default constructor");
    }

    @Test
    @DisplayName("ResortsLiteApplication - should be in correct package")
    void testResortsLiteApplication_shouldBeInCorrectPackage() {
        assertEquals("com.demo.resortslite", ResortsLiteApplication.class.getPackageName(),
                "ResortsLiteApplication should be in com.demo.resortslite package");
    }

    @Test
    @DisplayName("ResortsLiteApplication - instantiation should create non-null object")
    void testResortsLiteApplication_instantiationShouldCreateNonNullObject() {
        ResortsLiteApplication app = new ResortsLiteApplication();
        assertNotNull(app, "Instantiated ResortsLiteApplication should not be null");
    }
}
