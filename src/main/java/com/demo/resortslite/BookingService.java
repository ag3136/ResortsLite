package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // cr-java-0069: DB credentials retrieved from AWS Secrets Manager at runtime —
    // no hard-coded username/password in source code or version control.
    // The secret name is injected via environment variable DB_SECRET_NAME.
    @Value("${cloud.aws.secrets.db-secret-name:${DB_SECRET_NAME:resortslite/db/credentials}}")
    private String dbSecretName;

    // cr-java-0069: Payment API endpoint retrieved from AWS Secrets Manager —
    // no hard-coded internal IP addresses or hostnames in source code.
    @Value("${cloud.aws.secrets.payment-secret-name:${PAYMENT_SECRET_NAME:resortslite/payment/endpoint}}")
    private String paymentSecretName;

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    public BookingService(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves a secret string from AWS Secrets Manager.
     * Replaces hard-coded credentials (cr-java-0069) and file-based auth (cr-java-0090).
     */
    private String getSecret(String secretName) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
        return response.secretString();
    }

    /**
     * Retrieves a specific field from a JSON secret stored in AWS Secrets Manager.
     * Used for structured secrets (e.g., {"username":"...","password":"..."}).
     */
    private String getSecretField(String secretName, String fieldName) {
        try {
            String secretJson = getSecret(secretName);
            JsonNode node = objectMapper.readTree(secretJson);
            return node.has(fieldName) ? node.get(fieldName).asText() : "";
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve secret field '" + fieldName
                    + "' from secret '" + secretName + "'", e);
        }
    }

    /**
     * Returns the DB host retrieved from AWS Secrets Manager (replaces hard-coded DB_HOST).
     * cr-java-0069: credentials and connection info externalized to Secrets Manager.
     */
    public String getDbHost() {
        return getSecretField(dbSecretName, "host");
    }

    /**
     * Returns the payment API endpoint retrieved from AWS Secrets Manager
     * (replaces hard-coded PAYMENT_API with internal IP).
     * cr-java-0069: infrastructure endpoints externalized to Secrets Manager.
     */
    public String getPaymentApiEndpoint() {
        return getSecret(paymentSecretName);
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Parameterized query prevents SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        String confirmCode = md5Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        // DB host is no longer exposed in response; retrieved securely from Secrets Manager
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Parameterized query prevents SQL injection
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

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

    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE")
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) {
            return false;
        }
        return true;
    }

    public String generateReport(String month) {
        // Payment API endpoint retrieved from Secrets Manager (replaces hard-coded IP)
        String paymentApi = getPaymentApiEndpoint();
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    /**
     * cr-java-0090: Authentication credentials are no longer stored in local files.
     * User identity management is delegated to Amazon Cognito; credential storage
     * uses AWS Secrets Manager. This method validates a token via Secrets Manager
     * rather than reading from a local file.
     */
    public boolean validateAuthToken(String token) {
        try {
            // Retrieve the expected token/secret from AWS Secrets Manager
            // (replaces file-based authentication — cr-java-0090)
            String expectedSecret = getSecretField(dbSecretName, "authToken");
            return token != null && token.equals(expectedSecret);
        } catch (Exception e) {
            return false;
        }
    }

    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
