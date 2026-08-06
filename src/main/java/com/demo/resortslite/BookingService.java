package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    @Autowired
    private AwsCognitoConfig awsCognitoConfig;

    // Database credentials are now retrieved from AWS Secrets Manager via AwsSecretsManagerConfig.
    // This enables secure credential management with automatic rotation support.
    // Lines 22-23 previously contained: DB_USER = "admin" and DB_PASS = "Resort$Pass#2019!"

    // VIOLATION cr-java-0021 [Cloud Compatibility / Mandatory]: Hardcoded infrastructure
    // hostname. Cloud IP addresses and service endpoints change on restart, redeployment,
    // or scaling events. Must be externalised to environment variables / Parameter Store.
    private static final String PAYMENT_API = "http://10.0.1.45:9090/payments/charge"; // cr-java-0021, cr-java-0088

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // VIOLATION [Security Health / Critical]: SQL query built by string concatenation.
        // An attacker can pass guestName = "'; DROP TABLE bookings; --" to destroy data.
        // Use parameterised queries (JdbcTemplate with '?') to prevent SQL injection.
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES ('" // sql-inject-001
                + bookingId + "', '" + guestName + "', '" + roomType               // sql-inject-001
                + "', '" + checkIn + "', '" + checkOut + "')";                     // sql-inject-001
        jdbcTemplate.execute(sql);

        // VIOLATION [Security Health / High]: MD5 is a broken hash algorithm (RFC 6151).
        // Do not use MD5 for any security-related hashing. Use SHA-256 or bcrypt.
        String confirmCode = md5Hash(bookingId + guestName); // sec-weak-hash-001

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        // FIXED: Using AWS Secrets Manager to retrieve DB host instead of hard-coded value
        booking.put("dbHost", awsSecretsManagerConfig.getDbHost());
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // VIOLATION [Security Health / Critical]: SQL injection via string concatenation.
        // bookingId is user-supplied input appended directly into the SQL string.
        String sql = "SELECT * FROM bookings WHERE id = '" + bookingId + "'"; // sql-inject-001
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    // VIOLATION [Code Sustainability / High]: High cyclomatic complexity.
    // This method has 9+ decision branches. Automated transformation tools flag methods
    // above complexity threshold as high maintenance risk and transformation blockers.
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
        // VIOLATION [Code Sustainability / Medium]: Duplicated validation logic.
        // Same room type validation is repeated here and in calculateRoomPrice.
        // Should be extracted to a shared RoomType enum or validator.
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE") // dup-logic-001
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) { // dup-logic-001
            return false;
        }
        return true;
    }
     * FIXED [cr-java-0090]: Replaced file-based authentication with AWS Cognito
     * 
     * Get database credentials from AWS Secrets Manager.
     * User authentication is now handled by AWS Cognito (see authenticateUser method).
     * This method demonstrates how to access credentials securely
     */
    public Map<String, String> getDatabaseCredentials() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("host", awsSecretsManagerConfig.getDbHost());

    /**
     * FIXED [cr-java-0090]: Authenticate user with AWS Cognito
     * 
     * Replaces file-based authentication with cloud-native identity management.
     * AWS Cognito provides centralized, encrypted, and auditable authentication
     * with built-in user lifecycle management.
     * 
     * @param username User's username
     * @param password User's password
     * @return Authentication result containing tokens and user info
     */
    public Map<String, Object> authenticateUser(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Authenticate with AWS Cognito
            Map<String, String> tokens = awsCognitoConfig.authenticateUser(username, password);
            
            result.put("success", true);
            result.put("accessToken", tokens.get("accessToken"));
            result.put("idToken", tokens.get("idToken"));
            result.put("refreshToken", tokens.get("refreshToken"));
            result.put("tokenType", tokens.get("tokenType"));
            result.put("expiresIn", tokens.get("expiresIn"));
            result.put("message", "Authentication successful");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "Authentication failed");
        }
        return result;
    }

    /**
     * FIXED [cr-java-0090]: Verify user token with AWS Cognito
     * 
     * @param accessToken JWT access token from Cognito
     * @return User information if token is valid
     */
    public Map<String, Object> verifyUserToken(String accessToken) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> userAttributes = awsCognitoConfig.verifyToken(accessToken);
            
            result.put("valid", true);
            result.put("username", userAttributes.get("username"));
            result.put("email", userAttributes.get("email"));
            result.put("attributes", userAttributes);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * FIXED [cr-java-0090]: Get user details from AWS Cognito
     * 
     * @param username User's username
     * @return User details from Cognito User Pool
     */
    public Map<String, Object> getUserDetails(String username) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> userDetails = awsCognitoConfig.getUserDetails(username);
            
            result.put("success", true);
            result.put("username", userDetails.get("username"));
            result.put("userStatus", userDetails.get("userStatus"));
            result.put("enabled", userDetails.get("enabled"));
            result.put("details", userDetails);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private String md5Hash(String input) { // sec-weak-hash-001
        try {
            MessageDigest md = MessageDigest.getInstance("MD5"); // sec-weak-hash-001
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
