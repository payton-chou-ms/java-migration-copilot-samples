package com.microsoft.migration.assets.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceBusConfig {
    public static final String IMAGE_PROCESSING_QUEUE = "image-processing";

    @Value("${spring.cloud.azure.servicebus.namespace}")
    private String namespace;

    @Bean(destroyMethod = "close")
    public ServiceBusSenderClient imageProcessingSenderClient() {
        return new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(namespace + ".servicebus.windows.net")
                .credential(new DefaultAzureCredentialBuilder().build())
                .sender()
                .queueName(IMAGE_PROCESSING_QUEUE)
                .buildClient();
    }
}
