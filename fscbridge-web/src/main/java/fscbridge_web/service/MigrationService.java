package fscbridge_web.service;

import fscbridge_audit.service.AuditService;
import fscbridge_core.enums.JobStatus;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.MigrationJob;
import fscbridge_core.model.SalesforceRecord;
import fscbridge_connector.client.SalesforceClient;
import fscbridge_mapper.service.FieldMapperService;
import fscbridge_web.kafka.producer.MigrationEventProducer;
import fscbridge_web.metrics.MigrationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final SalesforceClient salesforceClient;
    private final FieldMapperService fieldMapperService;
    private final AuditService auditService;
    private final MigrationMetrics migrationMetrics;
    private final MigrationEventProducer eventProducer;

    @Value("${migration.max-records:200}")
    private int maxRecords;

    public MigrationJob runMigration(MigrationJob job) {

        if (job.getJobId() == null) {
            job.setJobId(UUID.randomUUID().toString());
        }
        log.info("Starting migration job: {} | dryRun: {}",
                job.getJobId(), job.isDryRun());

        migrationMetrics.recordJobStarted(job.isDryRun());
        long startTime = System.currentTimeMillis();

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        auditService.logJobStarted(job);

        eventProducer.publishJobStarted(job);

        try {
            List<SalesforceRecord> sourceRecords;

            log.info("Connecting to Salesforce...");

            String soqlQuery = buildSoqlQuery(job.getSourceObject());

            sourceRecords = salesforceClient.queryRecords(soqlQuery);

            log.info("Successfully fetched {} records from Salesforce",
                    sourceRecords.size());

            if (job.isDryRun()) {
                log.info("DRY RUN ENABLED - No records will be inserted");
            }

            job.setTotalRecords(sourceRecords.size());
            log.info("Processing {} records", sourceRecords.size());

            int successCount = 0;
            int failureCount = 0;
            List<SalesforceRecord> processedRecords = new ArrayList<>();

            for (SalesforceRecord sourceRecord : sourceRecords) {
                try {
                    SalesforceRecord mappedRecord = fieldMapperService.mapRecord(
                            sourceRecord,
                            job.getTargetObject()
                    );

                    if (job.isDryRun()) {
                        log.debug("DRY RUN: Would insert record {} as {}",
                                sourceRecord.getId(), job.getTargetObject());
                        mappedRecord.setMigrated(true);
                        successCount++;

                        eventProducer.publishRecordMigrated(
                                job.getJobId(),
                                sourceRecord,
                                "DRY_RUN",
                                successCount,
                                sourceRecords.size());
                    } else {
                        String targetId = salesforceClient.insertRecord(
                                job.getTargetObject(),
                                mappedRecord.getFields()
                        );
                        mappedRecord.setMigrated(true);
                        mappedRecord.setTargetId(targetId);
                        auditService.logRecordSuccess(
                                job.getJobId(), sourceRecord, targetId);
                        successCount++;

                        eventProducer.publishRecordMigrated(
                                job.getJobId(),
                                sourceRecord,
                                targetId,
                                successCount,
                                sourceRecords.size());
                    }
                    processedRecords.add(mappedRecord);

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process record {}: {}",
                            sourceRecord.getId(), e.getMessage());
                    auditService.logRecordFailure(
                            job.getJobId(), sourceRecord, e.getMessage());

                    eventProducer.publishRecordFailed(
                            job.getJobId(), sourceRecord, e.getMessage());

                    sourceRecord.setMigrated(false);
                    sourceRecord.setErrorMessage(e.getMessage());
                    processedRecords.add(sourceRecord);
                }
            }

            job.setSuccessCount(successCount);
            job.setFailureCount(failureCount);
            job.setRecords(processedRecords);
            job.setCompletedAt(LocalDateTime.now());
            job.setStatus(failureCount == 0 ? JobStatus.COMPLETED : JobStatus.FAILED);

            auditService.logJobCompleted(job);

            eventProducer.publishJobCompleted(job);

            long duration = System.currentTimeMillis() - startTime;
            migrationMetrics.recordJobCompleted(duration, successCount, failureCount);

            log.info("Job {} completed. Success: {} | Failed: {} | {}ms",
                    job.getJobId(), successCount, failureCount, duration);

            return job;

        } catch (Exception e) {
            log.error("Job {} failed: {}", job.getJobId(), e.getMessage());
            migrationMetrics.recordJobFailed();

            eventProducer.publishJobFailed(
                    job.getJobId(), job.getJobName(), e.getMessage());

            job.setStatus(JobStatus.FAILED);
            job.setFailureReason(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            auditService.logJobFailed(job.getJobId(), e.getMessage());
            throw new FsBridgeException("JOB_FAILED",
                    "Migration job failed: " + e.getMessage(), e);
        }
    }
    public int rollback(String jobId, String objectType) {
        log.info("Starting rollback for job: {}", jobId);

        migrationMetrics.recordRollback();
        auditService.logRollbackStarted(jobId);
        eventProducer.publishRollbackStarted(jobId);

        List<String> targetIds = auditService.getTargetIdsForRollback(jobId);

        if (targetIds.isEmpty()) {
            log.warn("No records found to rollback for job: {}", jobId);
            auditService.logRollbackCompleted(jobId, 0);
            return 0;
        }

        int deletedCount = 0;

        for (String targetId : targetIds) {
            try {
                salesforceClient.deleteRecord(objectType, targetId);
                deletedCount++;
                log.debug("Rollback deleted record: {}", targetId);
            } catch (Exception e) {
                log.error("Failed to delete record {} during rollback: {}",
                        targetId, e.getMessage());
            }
        }

        auditService.logRollbackCompleted(jobId, deletedCount);
        eventProducer.publishRollbackCompleted(jobId, deletedCount);

        log.info("Rollback complete for job {}. Deleted {} records.", jobId, deletedCount);
        return deletedCount;
    }


    private String buildSoqlQuery(String objectType) {
        return "SELECT FIELDS(ALL) FROM " + objectType + " LIMIT " + maxRecords;
    }
}