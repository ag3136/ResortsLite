package com.demo.resortslite;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Inventory Controller - Demonstrates microservices decomposition for cz-java-0082
 * 
 * This controller represents the inventory microservice that should be deployed
 * as a separate GKE Autopilot pod. In a full microservices architecture, this
 * would be in a separate project/repository with its own:
 * - Container image
 * - Kubernetes deployment
 * - GCP Workload Identity binding
 * - Secret Manager configuration
 * - Independent scaling policies
 * 
 * For now, it's included in the same codebase to demonstrate the decoupling pattern.
 * In production, this should be extracted to a separate inventory-service project.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    /**
     * Check room availability endpoint.
     * This endpoint is called by the booking service to check room availability.
     * 
     * In a microservices architecture:
     * - This service can scale independently based on inventory query load
     * - Can use its own database/cache (e.g., Cloud Memorystore for Redis)
     * - Can be deployed to different GKE node pools based on resource requirements
     * - Has its own health checks and monitoring
     */
    @GetMapping("/check")
    public Map<String, Object> checkRoomAvailability(@RequestParam String roomType) {
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        
        // Business logic for checking availability
        // In production, this would query inventory database/cache
        boolean available = isValidRoomType(roomType);
        
        response.put("available", available);
        response.put("service", "inventory-microservice");
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * Room type validation logic.
     * This was previously in BookingService.isRoomAvailable() - now decoupled.
     */
    private boolean isValidRoomType(String roomType) {
        return roomType.equals("STANDARD") 
            || roomType.equals("DELUXE") 
            || roomType.equals("SUITE") 
            || roomType.equals("VILLA");
    }

    /**
     * Health check endpoint for this microservice.
     * In production, this would be used by Kubernetes liveness/readiness probes.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "inventory-microservice");
        return health;
    }
}
