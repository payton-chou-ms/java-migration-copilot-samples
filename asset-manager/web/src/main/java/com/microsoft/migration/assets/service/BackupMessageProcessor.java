package com.microsoft.migration.assets.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static com.microsoft.migration.assets.config.ServiceBusConfig.IMAGE_PROCESSING_QUEUE;

/**
 * A backup message processor that serves as a monitoring and logging service.
 *
 * Only enabled when the "backup" profile is active.
 */
@Slf4j
@Component
@Profile("backup")
public class BackupMessageProcessor implements SmartLifecycle {

    @Value("${spring.cloud.azure.servicebus.namespace}")
    private String namespace;

    @Autowired
    private ObjectMapper objectMapper;

    private ServiceBusProcessorClient processorClient;
    private volatile boolean running = false;

    @Override
    public void start() {
        if (running) return;
        processorClient = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(namespace + ".servicebus.windows.net")
                .credential(new DefaultAzureCredentialBuilder().build())
                .processor()
                .queueName(IMAGE_PROCESSING_QUEUE)
                .processMessage(context -> {
                    String body = context.getMessage().getBody().toString();
                    try {
                        ImageProcessingMessage message = objectMapper.readValue(body, ImageProcessingMessage.class);
                        log.info("[BACKUP] Monitoring message: {}", message.getKey());
                        log.info("[BACKUP] Content type: {}, Storage: {}, Size: {}",
                                message.getContentType(), message.getStorageType(), message.getSize());
                        context.complete();
                        log.info("[BACKUP] Successfully processed message: {}", message.getKey());
                    } catch (Exception e) {
                        log.error("[BACKUP] Failed to process message: {}", body, e);
                        context.abandon();
                        log.warn("[BACKUP] Message abandoned for retry: {}", body);
                    }
                })
                .processError(context -> log.error("[BACKUP] Service Bus error: {}",
                        context.getException().getMessage(), context.getException()))
                .buildProcessorClient();
        processorClient.start();
        running = true;
    }

    @Override
    public void stop() {
        if (!running) return;
        if (processorClient != null) {
            processorClient.close();
            processorClient = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}