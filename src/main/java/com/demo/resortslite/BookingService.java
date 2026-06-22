package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final String DEFAULT_KEY_VAULT_URL = "https://localhost.vault.azure.net/";
    private static final String DEFAULT_PAYMENT_ENDPOINT = "https://payments.example.internal/payments/charge";
    private static final String DEFAULT_AUTH_PROVIDER = "azure-active-directory";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SecretClient secretClient;
    private final String dbHost;
    private final String dbUser;
    private final String dbPass;
    private final String paymentApi;
    private final String authenticationProvider;

    public BookingService() {
        this.secretClient = buildSecretClient();
        this.dbHost = getSecretValue("db-host", "DB_HOST", "jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1");
        this.dbUser = getSecretValue("db-user", "DB_USER", "sa");
        this.dbPass = getSecretValue("db-password", "DB_PASSWORD", "");
        this.paymentApi = getSecretValue("payment-api-endpoint", "PAYMENT_API_ENDPOINT", DEFAULT_PAYMENT_ENDPOINT);
        this.authenticationProvider = getSecretValue("auth-provider", "AUTH_PROVIDER", DEFAULT_AUTH_PROVIDER);
    }

    public Map<String, Object> createBooking(String guestName, String roomType,
                                             String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbHost);
        booking.put("dbUser", dbUser);
        booking.put("dbPasswordConfigured", dbPass != null);
        booking.put("authenticationProvider", authenticationProvider);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
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
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    private SecretClient buildSecretClient() {
        String keyVaultUrl = System.getenv().getOrDefault("AZURE_KEY_VAULT_URL", DEFAULT_KEY_VAULT_URL);
        try {
            return new SecretClientBuilder()
                    .vaultUrl(keyVaultUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        } catch (Exception ex) {
            return null;
        }
    }

    private String getSecretValue(String secretName, String envName, String defaultValue) {
        try {
            if (secretClient != null) {
                return secretClient.getSecret(secretName).getValue();
            }
        } catch (Exception ignored) {
        }
        return System.getenv().getOrDefault(envName, defaultValue);
    }

    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
