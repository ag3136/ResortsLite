package com.demo.resortslite;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AzureServiceBusScheduler {

    private final String connectionString;
    private final String queueName;

    public AzureServiceBusScheduler(
            @Value("${azure.servicebus.connection-string:}") String connectionString,
            @Value("${azure.servicebus.queue-name:report-jobs}") String queueName) {
        this.connectionString = connectionString;
        this.queueName = queueName;
    }

    public void scheduleReportGeneration(String month, String year, OffsetDateTime scheduledTime) {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            return;
        }
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
        try {
            ServiceBusMessage message = new ServiceBusMessage("generate-report:" + month + ":" + year)
                    .setScheduledEnqueueTime(scheduledTime);
            senderClient.scheduleMessage(message, scheduledTime);
        } finally {
            senderClient.close();
        }
    }
}
