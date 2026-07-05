package fscbridge_web.kafka.consumer;

import fscbridge_web.kafka.config.KafkaConfig;
import fscbridge_web.kafka.event.MigrationEvent;
import fscbridge_web.metrics.MigrationMetrics;
import fscbridge_web.websocket.MigrationProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationEventConsumer {

    private final MigrationMetrics migrationMetrics;
    private final MigrationProgressHandler progressHandler;
    @KafkaListener(
            topics = KafkaConfig.MIGRATION_EVENTS_TOPIC,
            groupId = "fscbridge-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMigrationEvent(
            @Payload MigrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Received event: {} | job: {} | partition: {} | offset: {}",
                event.getEventType(),
                event.getJobId(),
                partition,
                offset);

        try {
            switch (event.getEventType()) {
                case "JOB_STARTED" -> handleJobStarted(event);
                case "RECORD_MIGRATED" -> handleRecordMigrated(event);
                case "RECORD_FAILED" -> handleRecordFailed(event);
                case "JOB_COMPLETED" -> handleJobCompleted(event);
                case "JOB_FAILED" -> handleJobFailed(event);
                case "ROLLBACK_STARTED" -> handleRollbackStarted(event);
                case "ROLLBACK_COMPLETED" -> handleRollbackCompleted(event);
                default -> log.warn("Unknown event type: {}", event.getEventType());
            }
            progressHandler.sendProgressUpdate(event);
        } catch (Exception e) {
            log.error("Error processing event {} for job {}: {}",
                    event.getEventType(),
                    event.getJobId(),
                    e.getMessage());
        }
    }

    @KafkaListener(
            topics = KafkaConfig.MIGRATION_ERRORS_TOPIC,
            groupId = "fscbridge-error-group"
    )
    public void consumeErrorEvent(@Payload MigrationEvent event) {
        log.error("ERROR EVENT received: {} | job: {} | message: {}",
                event.getEventType(),
                event.getJobId(),
                event.getMessage());

        progressHandler.sendProgressUpdate(event);
    }

    private void handleJobStarted(MigrationEvent event) {
        log.info("Migration started: {} | source: {} | dryRun: {}",
                event.getJobName(),
                event.getSourceObject(),
                event.isDryRun());

        migrationMetrics.recordJobStarted(event.isDryRun());
    }

    private void handleRecordMigrated(MigrationEvent event) {
        log.debug("Record migrated: {} -> {} | progress: {}%",
                event.getSourceRecordId(),
                event.getTargetRecordId(),
                String.format("%.1f", event.getProgressPercent()));

        if (event.getSuccessCount() % 10 == 0) {
            log.info("Progress: {}/{} records | {}%",
                    event.getSuccessCount(),
                    event.getTotalRecords(),
                    event.getProgressPercent());
        }
    }

    private void handleRecordFailed(MigrationEvent event) {
        log.warn("Record failed: {} | reason: {}",
                event.getSourceRecordId(),
                event.getErrorMessage());
    }

    private void handleJobCompleted(MigrationEvent event) {
        log.info("Migration COMPLETED: {} | {}/{} records | {} failed",
                event.getJobName(),
                event.getSuccessCount(),
                event.getTotalRecords(),
                event.getFailureCount());

        migrationMetrics.recordJobCompleted(
                0L,
                event.getSuccessCount(),
                event.getFailureCount());

        if (event.getFailureCount() > 0) {
            log.warn("Job completed with {} failures. Check audit log for details.",
                    event.getFailureCount());
        }
    }

    private void handleJobFailed(MigrationEvent event) {
        log.error("Migration FAILED: {} | reason: {}",
                event.getJobName(),
                event.getErrorMessage());

        migrationMetrics.recordJobFailed();
    }

    private void handleRollbackStarted(MigrationEvent event) {
        log.info("Rollback started for job: {}", event.getJobId());
        migrationMetrics.recordRollback();
    }

    private void handleRollbackCompleted(MigrationEvent event) {
        log.info("Rollback completed: {} records deleted from target org",
                event.getSuccessCount());
    }
}
