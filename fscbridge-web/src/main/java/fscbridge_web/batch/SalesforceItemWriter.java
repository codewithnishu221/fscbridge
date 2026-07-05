package fscbridge_web.batch;

import fscbridge_audit.service.AuditService;
import fscbridge_connector.client.SalesforceClient;
import fscbridge_core.model.SalesforceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;


@Slf4j
@RequiredArgsConstructor
public class SalesforceItemWriter implements ItemWriter<SalesforceRecord> {

    private final SalesforceClient salesforceClient;
    private final AuditService auditService;
    private final String jobId;
    private final String targetObject;
    private final boolean dryRun;


    @Override
    public void write(Chunk<? extends SalesforceRecord> chunk) throws Exception {
        log.info("Writing chunk of {} records to {}",
                chunk.size(), targetObject);

        for (SalesforceRecord record : chunk) {
            try {
                if (dryRun) {
                    log.debug("DRY RUN: Would insert record {} into {}",
                            record.getId(), targetObject);

                    auditService.logRecordSuccess(
                            jobId, record, "DRY_RUN_NO_ID");

                } else {
                    String targetId = salesforceClient.insertRecord(
                            targetObject,
                            record.getFields()
                    );

                    log.debug("Inserted record {} → target ID: {}",
                            record.getId(), targetId);

                    auditService.logRecordSuccess(jobId, record, targetId);
                }

            } catch (Exception e) {
                log.error("Failed to write record {}: {}",
                        record.getId(), e.getMessage());

                auditService.logRecordFailure(
                        jobId, record, e.getMessage());
            }
        }

        log.info("Chunk write completed for {} records", chunk.size());
    }
}