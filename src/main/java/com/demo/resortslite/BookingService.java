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
    private JdbcTemplate jdbcTemplate;

    // NOTE: Hardcoded database credentials in source code is a security risk.
    // For production, externalise to AWS Secrets Manager, Parameter Store, or
    // environment variables. Never commit credentials to version control.
    private static final String DB_HOST = "db-prod.resorts-internal.com";
    private static final String DB_USER = "admin";
    private static final String DB_PASS = "Resort$Pass#2019!";

    // NOTE: Hardcoded infrastructure hostname. Cloud IP addresses and service endpoints
    // change on restart/redeployment. Externalise to environment variables or Parameter Store.
    private static final String PAYMENT_API = "http://10.0.1.45:9090/payments/charge";

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // NOTE: SQL query built by string concatenation is vulnerable to SQL injection.
        // Use parameterised queries (JdbcTemplate with '?') to prevent SQL injection.
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES ('"
                + bookingId + "', '" + guestName + "', '" + roomType
                + "', '" + checkIn + "', '" + checkOut + "')";
        jdbcTemplate.execute(sql);

        // FIX issue-3 (high / security):
        // Replaced MD5 (cryptographically broken per RFC 6151) with SHA-256.
        // SHA-256 is the recommended minimum for security-sensitive hashing in Java 11+.
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", DB_HOST);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // NOTE: SQL injection via string concatenation — use parameterised queries.
        String sql = "SELECT * FROM bookings WHERE id = '" + bookingId + "'";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates the total room price based on room type, number of nights,
     * season, and loyalty tier.
     *
     * @param roomType one of STANDARD, DELUXE, SUITE, VILLA
     * @param nights   number of nights to stay
     * @param season   PEAK, OFF, or standard
     * @param loyalty  GOLD, PLATINUM, DIAMOND, or none
     * @return formatted total price string
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice;
        switch (roomType) {
            case "DELUXE":  basePrice = 200.0; break;
            case "SUITE":   basePrice = 350.0; break;
            case "VILLA":   basePrice = 600.0; break;
            default:        basePrice = 120.0; break; // STANDARD and unknown types
        }

        if ("PEAK".equals(season))      { basePrice *= 1.5; }
        else if ("OFF".equals(season))  { basePrice *= 0.8; }

        if ("GOLD".equals(loyalty))         { basePrice *= 0.9; }
        else if ("PLATINUM".equals(loyalty)) { basePrice *= 0.8; }
        else if ("DIAMOND".equals(loyalty))  { basePrice *= 0.7; }

        // NOTE: >= 14 check must come before >= 7 to apply the larger discount correctly.
        if (nights >= 14)      { basePrice *= 0.90; }
        else if (nights >= 7)  { basePrice *= 0.95; }

        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    /**
     * Checks whether a given room type is available for booking.
     *
     * @param roomType the room type to check
     * @return true if the room type is valid and available
     */
    public boolean isRoomAvailable(String roomType) {
        switch (roomType) {
            case "STANDARD":
            case "DELUXE":
            case "SUITE":
            case "VILLA":
                return true;
            default:
                return false;
        }
    }

    /**
     * Generates a report summary message for the given month.
     *
     * @param month the month identifier
     * @return report generation status message
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + PAYMENT_API;
    }

    /**
     * Computes a SHA-256 hex digest of the given input string.
     * Replaces the former MD5-based implementation which used a cryptographically
     * broken algorithm (RFC 6151). SHA-256 is the recommended minimum for
     * security-sensitive hashing in Java 11+.
     *
     * @param input the string to hash
     * @return lowercase hex-encoded SHA-256 digest, or the original input on error
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
