package fscbridge_web.kafka.producer;

import fscbridge_core.enums.JobStatus;
import fscbridge_core.model.MigrationJob;
import fscbridge_core.model.SalesforceRecord;
import fscbridge_web.kafka.config.KafkaConfig;
import fscbridge_web.kafka.event.MigrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationEventProducer {

    private final KafkaTemplate<String, MigrationEvent> kafkaTemplate;

    public void publishJobStarted(MigrationJob job) {
        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("JOB_STARTED")
                .jobId(job.getJobId())
                .jobName(job.getJobName())
                .jobStatus(JobStatus.RUNNING)
                .sourceObject(job.getSourceObject())
                .targetObject(job.getTargetObject())
                .dryRun(job.isDryRun())
                .timestamp(LocalDateTime.now())
                .message("Migration job started: " + job.getJobName())
                .build();

        publishEvent(event);
    }

    public void publishRecordMigrated(String jobId,
                                       SalesforceRecord sourceRecord,
                                       String targetId,
                                       int successCount,
                                       int totalRecords) {

        double progress = totalRecords > 0
                ? (successCount * 100.0 / totalRecords)
                : 0;

        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RECORD_MIGRATED")
                .jobId(jobId)
                .jobStatus(JobStatus.RUNNING)
                .sourceRecordId(sourceRecord.getId())
                .targetRecordId(targetId)
                .successCount(successCount)
                .totalRecords(totalRecords)
                .progressPercent(progress)
                .dryRun(targetId.equals("DRY_RUN"))
                .timestamp(LocalDateTime.now())
                .message(String.format(
                        "Record migrated: %s -> %s (%.1f%% complete)",
                        sourceRecord.getId(), targetId, progress))
                .build();

        publishEvent(event);
    }

    public void publishRecordFailed(String jobId,
                                     SalesforceRecord sourceRecord,
                                     String errorMessage) {

        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("RECORD_FAILED")
                .jobId(jobId)
                .jobStatus(JobStatus.RUNNING)
                .sourceRecordId(sourceRecord.getId())
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .message("Record failed: " + sourceRecord.getId()
                        + " - " + errorMessage)
                .build();

        publishToErrorTopic(event);
    }

    public void publishJobCompleted(MigrationJob job) {
        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("JOB_COMPLETED")
                .jobId(job.getJobId())
                .jobName(job.getJobName())
                .jobStatus(job.getStatus())
                .sourceObject(job.getSourceObject())
                .targetObject(job.getTargetObject())
                .totalRecords(job.getTotalRecords())
                .successCount(job.getSuccessCount())
                .failureCount(job.getFailureCount())
                .progressPercent(100.0)
                .dryRun(job.isDryRun())
                .timestamp(LocalDateTime.now())
                .message(String.format(
                        "Job completed: %d/%d records migrated. %d failed.",
                        job.getSuccessCount(),
                        job.getTotalRecords(),
                        job.getFailureCount()))
                .build();

        publishEvent(event);
    }

    public void publishJobFailed(String jobId,
                                  String jobName,
                                  String errorMessage) {

        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("JOB_FAILED")
                .jobId(jobId)
                .jobName(jobName)
                .jobStatus(JobStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .message("Job FAILED: " + jobName
                        + " - " + errorMessage)
                .build();

        publishToErrorTopic(event);
    }

    public void publishRollbackStarted(String jobId) {
        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ROLLBACK_STARTED")
                .jobId(jobId)
                .jobStatus(JobStatus.ROLLED_BACK)
                .timestamp(LocalDateTime.now())
                .message("Rollback started for job: " + jobId)
                .build();

        publishEvent(event);
    }

    public void publishRollbackCompleted(String jobId,
                                          int deletedCount) {

        MigrationEvent event = MigrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ROLLBACK_COMPLETED")
                .jobId(jobId)
                .jobStatus(JobStatus.ROLLED_BACK)
                .successCount(deletedCount)
                .timestamp(LocalDateTime.now())
                .message("Rollback completed: " + deletedCount
                        + " records deleted from target org.")
                .build();

        publishEvent(event);
    }

    private void publishEvent(MigrationEvent event) {
        try {
            CompletableFuture<SendResult<String, MigrationEvent>> future =
                    kafkaTemplate.send(
                            KafkaConfig.MIGRATION_EVENTS_TOPIC,
                            event.getJobId(),
                            event
                    );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Event published: {} | topic: {} | partition: {}",
                            event.getEventType(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("Failed to publish event {}: {}",
                            event.getEventType(), ex.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Kafka publish error for event {}: {}",
                    event.getEventType(), e.getMessage());
        }
    }

    private void publishToErrorTopic(MigrationEvent event) {
        try {
            kafkaTemplate.send(
                    KafkaConfig.MIGRATION_ERRORS_TOPIC,
                    event.getJobId(),
                    event
            );
            log.debug("Error event published to error topic: {}",
                    event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish to error topic: {}",
                    e.getMessage());
        }
    }
}
