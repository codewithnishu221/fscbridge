package fscbridge_web.controller;

import fscbridge_audit.model.AuditLog;
import fscbridge_audit.service.AuditService;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.MigrationJob;
import fscbridge_web.service.MigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;
    private final AuditService auditService;


    @PostMapping("/start")
    public ResponseEntity<?> startMigration(@RequestBody MigrationJob job) {
        log.info("Received migration request: {} → {}",
                job.getSourceObject(), job.getTargetObject());

        try {
            MigrationJob completedJob = migrationService.runMigration(job);
            return ResponseEntity.ok(completedJob);

        } catch (FsBridgeException e) {
            log.error("Migration failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", e.getErrorCode(),
                            "message", e.getMessage()
                    ));
        }
    }


    @PostMapping("/dry-run")
    public ResponseEntity<?> dryRun(@RequestBody MigrationJob job) {
        log.info("Received dry run request: {} → {}",
                job.getSourceObject(), job.getTargetObject());


        job.setDryRun(true);

        try {
            MigrationJob result = migrationService.runMigration(job);
            return ResponseEntity.ok(result);

        } catch (FsBridgeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", e.getErrorCode(),
                            "message", e.getMessage()
                    ));
        }
    }


    @DeleteMapping("/rollback/{jobId}")
    public ResponseEntity<?> rollback(
            @PathVariable String jobId,
            @RequestParam String objectType) {

        log.info("Received rollback request for job: {}", jobId);

        try {
            int deletedCount = migrationService.rollback(jobId, objectType);
            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "deletedRecords", deletedCount,
                    "message", "Rollback completed successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "ROLLBACK_FAILED",
                            "message", e.getMessage()
                    ));
        }
    }


    @GetMapping("/audit/{jobId}")
    public ResponseEntity<List<AuditLog>> getAuditLog(@PathVariable String jobId) {
        log.info("Fetching audit log for job: {}", jobId);
        List<AuditLog> history = auditService.getJobHistory(jobId);
        return ResponseEntity.ok(history);
    }


    @GetMapping("/summary/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobSummary(@PathVariable String jobId) {
        long successCount = auditService.getSuccessCount(jobId);
        long failureCount = auditService.getFailureCount(jobId);

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "successCount", successCount,
                "failureCount", failureCount,
                "totalProcessed", successCount + failureCount
        ));
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "fscbridge-web",
                "message", "FSC-Bridge migration API is running"
        ));
    }
}