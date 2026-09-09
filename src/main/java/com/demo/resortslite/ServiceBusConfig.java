package com.demo.resortslite;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Service Bus configuration for distributed, timezone-agnostic
 * scheduled task execution. Replaces java.util.Timer and server-local
 * scheduling with cloud-native message scheduling.
 */
@Configuration
public class ServiceBusConfig {

    @Value("${azure.servicebus.connection-string:}")
    private String serviceBusConnectionString;

    @Value("${azure.servicebus.queue-name:resort-scheduled-tasks}")
    private String queueName;

    /**
     * Creates a ServiceBusSenderClient for sending scheduled messages
     * to Azure Service Bus. This replaces local java.util.Timer-based
     * scheduling with distributed, cloud-native task scheduling.
     *
     * @return the ServiceBusSenderClient
     */
    @Bean
    public ServiceBusSenderClient serviceBusSenderClient() {
        if (serviceBusConnectionString != null && !serviceBusConnectionString.isEmpty()) {
            return new ServiceBusClientBuilder()
                    .connectionString(serviceBusConnectionString)
                    .sender()
                    .queueName(queueName)
                    .buildClient();
        }
        return null;
    }
}
