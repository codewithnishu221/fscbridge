package fscbridge_web.batch;

import fscbridge_audit.service.AuditService;
import fscbridge_connector.client.SalesforceClient;
import fscbridge_core.model.SalesforceRecord;
import fscbridge_mapper.service.FieldMapperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class MigrationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SalesforceClient salesforceClient;
    private final FieldMapperService fieldMapperService;
    private final AuditService auditService;


    @Value("${migration.chunk-size:10}")
    private int chunkSize;

    @Value("${migration.max-records:200}")
    private int maxRecords;


    public Job createMigrationJob(String sourceObject,
                                  String targetObject,
                                  String jobId,
                                  boolean dryRun) {

        log.info("Creating Spring Batch job for: {} → {} | dryRun: {}",
                sourceObject, targetObject, dryRun);

        Step migrationStep = createMigrationStep(
                sourceObject, targetObject, jobId, dryRun);

        return new JobBuilder("migrationJob-" + jobId, jobRepository)
                .start(migrationStep)
                .build();
    }


    private Step createMigrationStep(String sourceObject,
                                     String targetObject,
                                     String jobId,
                                     boolean dryRun) {

        SalesforceItemReader reader = new SalesforceItemReader(
                salesforceClient, sourceObject, maxRecords);

        MappingItemProcessor processor = new MappingItemProcessor(
                fieldMapperService, targetObject);

        SalesforceItemWriter writer = new SalesforceItemWriter(
                salesforceClient, auditService, jobId, targetObject, dryRun);

        return new StepBuilder("migrationStep-" + jobId, jobRepository)
                .<SalesforceRecord, SalesforceRecord>chunk(
                        chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10)
                .build();
    }
}