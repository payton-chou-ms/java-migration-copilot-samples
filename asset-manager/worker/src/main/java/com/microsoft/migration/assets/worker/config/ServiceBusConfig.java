package com.microsoft.migration.assets.worker.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.microsoft.migration.assets.worker.service.AbstractFileProcessingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!dev")
public class ServiceBusConfig {
    public static final String IMAGE_PROCESSING_QUEUE = "image-processing";

    @Value("${spring.cloud.azure.servicebus.namespace}")
    private String namespace;

    @Bean(destroyMethod = "close")
    public ServiceBusProcessorClient serviceBusProcessorClient(AbstractFileProcessingService service) {
        return new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(namespace + ".servicebus.windows.net")
                .credential(new DefaultAzureCredentialBuilder().build())
                .processor()
                .queueName(IMAGE_PROCESSING_QUEUE)
                .processMessage(service::processMessage)
                .processError(service::processError)
                .buildProcessorClient();
    }
}

