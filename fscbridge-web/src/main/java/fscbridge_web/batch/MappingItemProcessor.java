package fscbridge_web.batch;

import fscbridge_core.model.SalesforceRecord;
import fscbridge_mapper.service.FieldMapperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;


@Slf4j
@RequiredArgsConstructor
public class MappingItemProcessor
        implements ItemProcessor<SalesforceRecord, SalesforceRecord> {

    private final FieldMapperService fieldMapperService;
    private final String targetObject;


    @Override
    public SalesforceRecord process(SalesforceRecord sourceRecord) throws Exception {
        log.debug("Processing record: {}", sourceRecord.getId());

        try {
            SalesforceRecord mappedRecord = fieldMapperService.mapRecord(
                    sourceRecord,
                    targetObject
            );

            log.debug("Successfully processed record: {} → ready for {}",
                    sourceRecord.getId(), targetObject);

            return mappedRecord;

        } catch (Exception e) {
            log.error("Failed to process record {}: {}. Skipping.",
                    sourceRecord.getId(), e.getMessage());
            return null;
        }
    }
}