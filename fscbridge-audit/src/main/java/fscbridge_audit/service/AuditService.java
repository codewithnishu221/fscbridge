package fscbridge_audit.service;


import fscbridge_audit.model.AuditLog;
import fscbridge_audit.repository.AuditLogRepository;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.MigrationJob;
import fscbridge_core.model.SalesforceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logJobStarted(MigrationJob job){
        log.info("Job started: {}", job.getJobId());

        AuditLog entry = AuditLog.builder()
                .jobId(job.getJobId())
                .action("JOB_STARTED")
                .objectType(job.getSourceObject())
                .success(true)
                .errorMessage("")
                .build();
        auditLogRepository.save(entry);

    }

    public  void logRecordSuccess(String jobId, SalesforceRecord sourceRecord, String targetId){
        AuditLog entry = AuditLog.builder()
                .jobId(jobId)
                .action("RECORD_MIGRATED")
                .sourceRecordId(sourceRecord.getId())
                .targetRecordId(targetId)
                .objectType(sourceRecord.getObjectType())
                .success(true)
                .errorMessage("")
                .build();
        auditLogRepository.save(entry);
        log.debug("Logged success: source={} -> target={}", sourceRecord.getId(), targetId);
    }

    public void logRecordFailure(String jobId, SalesforceRecord sourceRecord, String errorMessage){
        AuditLog entry = AuditLog.builder()
                .jobId(jobId)
                .action("RECORD_FAILED")
                .sourceRecordId(sourceRecord.getId())
                .targetRecordId(null)
                .objectType(sourceRecord.getObjectType())
                .success(false)
                .errorMessage(errorMessage)
                .build();

        auditLogRepository.save(entry);
        log.warn("Logged failure: source={} reason={}",
                sourceRecord.getId(), errorMessage);
    }

    public  void logJobCompleted(MigrationJob job){
        log.info("Job completed: {} | success={} failed={}",
                job.getJobId(),
                job.getSuccessCount(),
                job.getFailureCount());

        String summary = String.format(
                "Total: %d | Success: %d | Failed: %d",
                job.getTotalRecords(),
                job.getSuccessCount(),
                job.getFailureCount()
        );
        AuditLog entry = AuditLog.builder()
                .jobId(job.getJobId())
                .action("JOB_COMPLETED")
                .objectType(job.getSourceObject())
                .success(job.getFailureCount() == 0)
                .errorMessage(summary)
                .build();

        auditLogRepository.save(entry);
    }
    public void logJobFailed(String jobId, String errorMessage) {
        log.error("Job failed: {} | reason: {}", jobId, errorMessage);

        AuditLog entry = AuditLog.builder()
                .jobId(jobId)
                .action("JOB_FAILED")
                .success(false)
                .errorMessage(errorMessage)
                .build();

        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<String> getTargetIdsForRollback(String jobId) {
        log.info("Preparing rollback for job: {}", jobId);

        List<AuditLog> migratedEntries = auditLogRepository
                .findByJobIdAndAction(jobId, "RECORD_MIGRATED");

        List<String> targetIds = migratedEntries.stream()
                .map(AuditLog::getTargetRecordId)
                .filter(id -> id != null && !id.isEmpty())
                .toList();

        log.info("Found {} records to rollback for job {}",
                targetIds.size(), jobId);

        return targetIds;
    }

    public void logRollbackStarted(String jobId){
        log.info("Rollback started for job: {}", jobId);

        AuditLog entry = AuditLog.builder()
                .jobId(jobId)
                .action("ROLLBACK_STARTED")
                .success(true)
                .errorMessage("")
                .build();

        auditLogRepository.save(entry);
    }

    public void logRollbackCompleted(String jobId, int deletedCount) {
        log.info("Rollback completed for job: {} | deleted {} records",
                jobId, deletedCount);
        AuditLog entry = AuditLog.builder()
                .jobId(jobId)
                .action("ROLLBACK_COMPLETED")
                .success(true)
                .errorMessage("Deleted " + deletedCount + " records from target org")
                .build();
        auditLogRepository.save(entry);
    }
    @Transactional(readOnly = true)
    public List<AuditLog> getJobHistory(String jobId){
        return auditLogRepository.findByJobId(jobId);
    }
    @Transactional(readOnly = true)
    public long getSuccessCount(String jobId){
        return auditLogRepository.countByJobIdAndActionAndSuccess(
                jobId, "RECORD_MIGRATED", true
        );
    }
    @Transactional(readOnly = true)
    public long getFailureCount(String jobId){
        return auditLogRepository.countByJobIdAndActionAndSuccess(
                jobId, "RECORD_FAILED", false
        );
    }
}
