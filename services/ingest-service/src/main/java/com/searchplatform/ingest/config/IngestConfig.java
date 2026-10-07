package com.searchplatform.ingest.config;

import com.searchplatform.connector.Connector;
import com.searchplatform.connector.ConnectorClientService;
import com.searchplatform.connector.npm.NpmPackageConnector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class IngestConfig {

    @Bean
    public ConnectorClientService connectorClientService() {
        return new ConnectorClientService();
    }

    @Bean
    public Connector npmPackageConnector(ConnectorClientService connectorClientService) {
        return new NpmPackageConnector(connectorClientService);
    }

    // single thread: ticks of the ingest loop can never overlap
    @Bean
    public ThreadPoolTaskScheduler npmIngestScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("npm-ingest-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(25); // under spring.lifecycle.timeout-per-shutdown-phase (30s)
        return scheduler;
    }
}
