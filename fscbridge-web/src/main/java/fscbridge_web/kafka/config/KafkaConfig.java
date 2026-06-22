package fscbridge_web.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaConfig {

    public static final String MIGRATION_EVENTS_TOPIC = "fscbridge.migration.events";
    public static final String MIGRATION_ERRORS_TOPIC = "fscbridge.migration.errors";
    public static final String AUDIT_EVENTS_TOPIC = "fscbridge.audit.events";

    @Bean
    public NewTopic migrationEventsTopic() {
        return TopicBuilder
                .name(MIGRATION_EVENTS_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic migrationErrorsTopic() {
        return TopicBuilder
                .name(MIGRATION_ERRORS_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder
                .name(AUDIT_EVENTS_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
