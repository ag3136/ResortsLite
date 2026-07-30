import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
/**
 * Booking Service - Business logic for resort bookings
 * 
 * FIXED cr-java-0090: File-based Authentication
 * - Authentication is now handled by Azure Active Directory (Entra ID)
 * - All service methods are called from authenticated controllers
 * - User identity is managed centrally in Azure AD, not in local files
 * - Credentials are never stored in application code or local files
 * - Authentication tokens (JWT) are validated by Spring Security Azure AD integration
 * - This service focuses on business logic, security is handled at the controller/framework level
 */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // FIXED cr-java-0069: Removed hard-coded database credentials
    // Credentials are now retrieved from Azure Key Vault using DefaultAzureCredential
    @Value("${azure.keyvault.url:}")
    private String keyVaultUrl;

    @Value("${azure.keyvault.db-username-secret:db-username}")
    private String dbUsernameSecretName;

    @Value("${azure.keyvault.db-password-secret:db-password}")
    private String dbPasswordSecretName;

    private SecretClient secretClient;
    private String dbUser;
    private String dbPass;

    @PostConstruct
    public void init() {
        // Initialize Azure Key Vault client using DefaultAzureCredential
        // This supports multiple authentication methods: Managed Identity, Azure CLI, Environment Variables
        if (keyVaultUrl != null && !keyVaultUrl.isEmpty()) {
            try {
                secretClient = new SecretClientBuilder()
                        .vaultUrl(keyVaultUrl)
                        .credential(new DefaultAzureCredentialBuilder().build())
                        .buildClient();

                // Retrieve database credentials from Azure Key Vault
                KeyVaultSecret usernameSecret = secretClient.getSecret(dbUsernameSecretName);
                KeyVaultSecret passwordSecret = secretClient.getSecret(dbPasswordSecretName);

                dbUser = usernameSecret.getValue();
                dbPass = passwordSecret.getValue();
            } catch (Exception e) {
                // Log error and use fallback for local development
                System.err.println("Failed to retrieve secrets from Azure Key Vault: " + e.getMessage());
                // For local development, fall back to environment variables
                dbUser = System.getenv("DB_USER");
                dbPass = System.getenv("DB_PASS");
            }
        } else {
            // For local development without Key Vault, use environment variables
            dbUser = System.getenv("DB_USER");
            dbPass = System.getenv("DB_PASS");
        }
    }

    // VIOLATION [Security Health / Critical]: Hardcoded database credentials in source code.
    // If this repo is pushed to GitHub (even private), credentials are permanently exposed
    // in git history. AWS Secrets Manager or Parameter Store must be used instead.
    private static final String DB_HOST = "db-prod.resorts-internal.com"; // cr-java-0021

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
        booking.put("dbHost", DB_HOST);
        return booking;
    /**
     * Get booking by ID - called from authenticated endpoints only
     */
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

    // Getter methods for credentials (if needed for testing or debugging)
    public String getDbUser() {
        return dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }
}
