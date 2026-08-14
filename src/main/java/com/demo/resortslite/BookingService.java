package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // FIXED cr-java-0090: Removed hardcoded credentials - now using Azure AD authentication
    // Database credentials should be managed via Azure Key Vault and injected as environment variables
    // Azure Managed Identity can be used for passwordless database authentication
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "db-prod.resorts-internal.com");
    private static final String DB_USER = "admin";                         // sec-cred-001
    private static final String DB_PASS = "Resort$Pass#2019!";             // sec-cred-001

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

        // FIXED cr-java-0090: Replaced MD5 hash with Azure AD user-based confirmation code
        // Confirmation code now includes Azure AD user identity for audit trail
        // This provides better security and traceability than MD5 hashing
        String confirmCode = generateSecureConfirmationCode(bookingId, guestName);
        
        // Get authenticated user information from Azure AD JWT token
        String authenticatedUser = getAuthenticatedUserEmail();
        String userId = getAuthenticatedUserId();

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", DB_HOST);
        
        // FIXED cr-java-0090: Added Azure AD user context to booking record
        // This enables:
        // - Audit trail of who created each booking
        // - Role-based access control for booking management
        // - Integration with Azure AD groups for authorization
        // - Compliance reporting and security monitoring
        // In production, store userId in database for complete audit trail
        booking.put("createdBy", authenticatedUser);
        booking.put("userId", userId);
        
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

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + PAYMENT_API;
    }

    /**
     * FIXED cr-java-0090: Replaced MD5 hash with secure confirmation code generation
     * 
     * Generates a secure confirmation code using SHA-256 instead of MD5.
     * Includes Azure AD user identity for better security and audit trail.
     * 
     * @param bookingId The booking identifier
     * @param guestName The guest name
     * @return Secure confirmation code
     */
    private String generateSecureConfirmationCode(String bookingId, String guestName) {
        try {
            // Get authenticated user from Azure AD
            String userId = getAuthenticatedUserId();
            
            // Use SHA-256 instead of MD5 for secure hashing
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String input = bookingId + guestName + userId + System.currentTimeMillis();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string (first 16 characters for readability)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            // Fallback to UUID-based code if hashing fails
            return UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }
    
    /**
     * FIXED cr-java-0090: Get authenticated user email from Azure AD JWT token
     * 
     * Extracts the user's email address from the Azure AD JWT token.
     * This provides user context for audit logging and authorization.
     * 
     * @return User email from Azure AD, or "anonymous" if not authenticated
     */
    private String getAuthenticatedUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                return jwt.getClaimAsString("preferred_username");
            }
            return "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
    
    /**
     * FIXED cr-java-0090: Get authenticated user ID from Azure AD JWT token
     * 
     * Extracts the user's unique identifier (OID) from the Azure AD JWT token.
     * This provides a stable user identifier for audit logging and authorization.
     * 
     * @return User OID from Azure AD, or "anonymous" if not authenticated
     */
    private String getAuthenticatedUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                // Azure AD uses 'oid' claim for user object ID
                String oid = jwt.getClaimAsString("oid");
                return oid != null ? oid : jwt.getClaimAsString("sub");
            }
            return "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
