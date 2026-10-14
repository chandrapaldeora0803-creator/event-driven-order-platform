package com.chandrapal.orders.config;

import com.chandrapal.orders.service.OrderService;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration
public class InfraConfig {

    /** 3 partitions: up to 3 consumer instances can process orders in parallel. */
    @Bean
    public NewTopic orderEventsTopic(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /** Cached orders expire after 10 minutes as a safety net on top of explicit eviction. */
    @Bean
    public RedisCacheManagerBuilderCustomizer orderCacheTtl() {
        return builder -> builder.withCacheConfiguration(OrderService.CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues());
    }
}
