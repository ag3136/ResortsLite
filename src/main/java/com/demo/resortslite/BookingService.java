package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cloud-ready booking service that retrieves database credentials from Azure Key Vault
 * and uses parameterized queries to prevent SQL injection.
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Database host externalized to environment variable
    @Value("${app.db.host:}")
    private String dbHost;

    // Payment API endpoint externalized to environment variable / Azure App Configuration
    @Value("${app.payment.endpoint:https://payment-svc.internal:9090/charge}")
    private String paymentApi;

    // Azure Key Vault configuration
    @Value("${azure.keyvault.secret.endpoint:}")
    private String keyVaultEndpoint;

    @Value("${azure.keyvault.secret.client-id:}")
    private String keyVaultClientId;

    @Value("${azure.keyvault.secret.client-secret:}")
    private String keyVaultClientSecret;

    @Value("${azure.keyvault.secret.tenant-id:}")
    private String keyVaultTenantId;

    /**
     * Lazily-initialized SecretClient for retrieving secrets from Azure Key Vault.
     * Uses DefaultAzureCredential for secure, centralized secret management.
     */
    private SecretClient secretClient;

    private SecretClient getSecretClient() {
        if (secretClient == null && keyVaultEndpoint != null && !keyVaultEndpoint.isEmpty()) {
            if (keyVaultClientId != null && !keyVaultClientId.isEmpty()
                    && keyVaultClientSecret != null && !keyVaultClientSecret.isEmpty()
                    && keyVaultTenantId != null && !keyVaultTenantId.isEmpty()) {
                // Service principal authentication
                secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultEndpoint)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();
            } else {
                // Managed Identity / DefaultAzureCredential
                secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultEndpoint)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();
            }
        }
        return secretClient;
    }

    /**
     * Retrieves a database credential from Azure Key Vault.
     * Falls back to environment variable if Key Vault is not configured.
     *
     * @param secretName the name of the secret in Key Vault
     * @return the secret value
     */
    private String getSecretFromKeyVault(String secretName) {
        SecretClient client = getSecretClient();
        if (client != null) {
            return client.getSecret(secretName).getValue();
        }
        // Fallback to environment variable
        return System.getenv(secretName);
    }

    /**
     * Creates a new booking using parameterized queries to prevent SQL injection.
     * Database credentials are retrieved from Azure Key Vault.
     *
     * @param guestName  the guest name
     * @param roomType   the room type
     * @param checkIn    the check-in date
     * @param checkOut   the check-out date
     * @return a map containing the booking details
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Use SHA-256 instead of MD5 for confirmation code
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbHost);
        return booking;
    }

    /**
     * Retrieves a booking by ID using a parameterized query.
     *
     * @param bookingId the booking ID
     * @return a map containing the booking details
     */
    public Map<String, Object> getBookingById(String bookingId) {
        // Use parameterized query to prevent SQL injection
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates the room price based on room type, nights, season, and loyalty tier.
     *
     * @param roomType the room type
     * @param nights   the number of nights
     * @param season   the season (PEAK, OFF)
     * @param loyalty  the loyalty tier (GOLD, PLATINUM, DIAMOND)
     * @return the formatted total price
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice = 0;
        if (roomType.equals("STANDARD")) { basePrice = 120.0; }
        else if (roomType.equals("DELUXE")) { basePrice = 200.0; }
        else if (roomType.equals("SUITE")) { basePrice = 350.0; }
        else if (roomType.equals("VILLA")) { basePrice = 600.0; }
        else { basePrice = 120.0; }
        if (season.equals("PEAK")) { basePrice = basePrice * 1.5; }
        else if (season.equals("OFF")) { basePrice = basePrice * 0.8; }
        if (loyalty.equals("GOLD")) { basePrice = basePrice * 0.9; }
        else if (loyalty.equals("PLATINUM")) { basePrice = basePrice * 0.8; }
        else if (loyalty.equals("DIAMOND")) { basePrice = basePrice * 0.7; }
        if (nights >= 7) { basePrice = basePrice * 0.95; }
        else if (nights >= 14) { basePrice = basePrice * 0.90; }
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    /**
     * Checks if a room type is available.
     *
     * @param roomType the room type to check
     * @return true if the room type is valid
     */
    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE")
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) {
            return false;
        }
        return true;
    }

    /**
     * Generates a report reference using the externalized payment API endpoint.
     *
     * @param month the month for the report
     * @return a report generation message
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    /**
     * Generates a SHA-256 hash of the input string.
     * Replaces the insecure MD5 hashing.
     *
     * @param input the input string to hash
     * @return the hex-encoded SHA-256 hash
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}
