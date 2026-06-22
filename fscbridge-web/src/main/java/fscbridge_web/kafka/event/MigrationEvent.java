package fscbridge_web.kafka.event;

import fscbridge_core.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationEvent {

    private String eventId;
    private String eventType;
    private String jobId;
    private String jobName;
    private JobStatus jobStatus;
    private String sourceObject;
    private String targetObject;
    private int totalRecords;
    private int successCount;
    private int failureCount;
    private double progressPercent;
    private String sourceRecordId;
    private String targetRecordId;
    private String errorMessage;
    private boolean dryRun;
    private LocalDateTime timestamp;
    private String message;
}
