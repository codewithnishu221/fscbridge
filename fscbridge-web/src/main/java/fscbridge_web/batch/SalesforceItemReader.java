package fscbridge_web.batch;

import fscbridge_connector.client.SalesforceClient;
import fscbridge_core.model.SalesforceRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.util.List;

@Slf4j
public class SalesforceItemReader implements ItemReader<SalesforceRecord> {

    private final List<SalesforceRecord> records;
    private int currentIndex = 0;


    public SalesforceItemReader(SalesforceClient salesforceClient,
                                String sourceObject,
                                int limit) {
        log.info("Initializing SalesforceItemReader for object: {} limit: {}",
                sourceObject, limit);

        String soqlQuery = "SELECT FIELDS(ALL) FROM "
                + sourceObject
                + " LIMIT " + limit;

        this.records = salesforceClient.queryRecords(soqlQuery);

        log.info("SalesforceItemReader loaded {} records", this.records.size());
    }


    @Override
    public SalesforceRecord read()
            throws Exception, UnexpectedInputException,
            ParseException, NonTransientResourceException {

        if (currentIndex < records.size()) {
            SalesforceRecord record = records.get(currentIndex);
            currentIndex++;
            log.debug("Reading record {} of {}",
                    currentIndex, records.size());
            return record;
        }

        log.info("SalesforceItemReader exhausted all {} records", records.size());
        return null;
    }
}