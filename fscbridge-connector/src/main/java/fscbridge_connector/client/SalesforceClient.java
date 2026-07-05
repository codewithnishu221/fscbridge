package fscbridge_connector.client;

import fscbridge_connector.auth.OAuthService;
import fscbridge_connector.config.SalesforceProperties;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.SalesforceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesforceClient {

    private final OAuthService oAuthService;
    private final SalesforceProperties properties;
    private final RestTemplate restTemplate;


    @SuppressWarnings("unchecked")
    public List<SalesforceRecord> queryRecords(String soqlQuery) {
        log.info("Executing SOQL query: {}", soqlQuery);

        try {
            List<SalesforceRecord> allRecords = new ArrayList<>();
             String queryUrl = oAuthService.getInstanceUrl()
                    + "/services/data/"
                    + properties.getApiVersion()
                    + "/query?q="
                    + soqlQuery.replace(" ", "+");
            while(queryUrl!= null) {
                ResponseEntity<Map> response = executeWithRetry(
                        queryUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(buildHeaders()),
                        Map.class
                );

                Map<String, Object> responseBody = response.getBody();
                if (responseBody == null) {
                    log.warn("Empty response body from Salesforce query");
                    return Collections.emptyList();
                }

                List<Map<String, Object>> rawRecords =
                        (List<Map<String, Object>>) responseBody.get("records");

                int totalSize = (Integer) responseBody.getOrDefault("totalSize", 0);
                log.info("Query returned {} records", totalSize);

                for (Map<String, Object> raw : rawRecords) {

                    Map<String, Object> attributes =
                            (Map<String, Object>) raw.get("attributes");
                    allRecords.add(SalesforceRecord.builder()
                            .id((String) raw.get("Id"))
                            .objectType(attributes != null ?
                                    (String) attributes.get("type") : "Unknown")
                                    .fields(raw)
                                    .build());
                }
                boolean done = (Boolean) responseBody.getOrDefault("done", true);
                queryUrl = done ? null :
                        oAuthService.getInstanceUrl() + responseBody.get("nextRecordsUrl");
            }
            return allRecords;

        } catch (FsBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query Salesforce records: {}", e.getMessage());
            throw new FsBridgeException("QUERY_FAILED",
                    "Failed to query records: " + e.getMessage(), e);
        }
    }


    @SuppressWarnings("unchecked")
    public int countRecords(String soqlQuery) {
        log.info("Executing count query: {}", soqlQuery);

        try {
            String queryUrl = oAuthService.getInstanceUrl()
                    + "/services/data/"
                    + properties.getApiVersion()
                    + "/query?q="
                    + soqlQuery.replace(" ", "+");

            ResponseEntity<Map> response = executeWithRetry(
                    queryUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Empty response from count query");
                return 0;
            }

            int totalSize = (Integer) responseBody.getOrDefault("totalSize", 0);
            log.info("Count query returned {} records", totalSize);
            return totalSize;

        } catch (Exception e) {
            log.error("Failed to execute count query: {}", e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public String insertRecord(String objectType, Map<String, Object> fields) {
        log.debug("Inserting record into object: {}", objectType);

        try {

            String insertUrl = oAuthService.getInstanceUrl()
                    + "/services/data/"
                    + properties.getApiVersion()
                    + "/sobjects/"
                    + objectType;

            // Remove read-only fields that Salesforce rejects on insert
            // Id, CreatedDate etc cannot be set manually
            Map<String, Object> cleanFields = new HashMap<>(fields);
            cleanFields.remove("Id");
            cleanFields.remove("CreatedDate");
            cleanFields.remove("LastModifiedDate");
            cleanFields.remove("SystemModstamp");
            cleanFields.remove("attributes");

            // Make the POST request
            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(cleanFields, buildHeaders());

            ResponseEntity<Map> response = executeWithRetry(
                    insertUrl,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            // Salesforce returns {"id": "NEW_RECORD_ID", "success": true}
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("success")) {
                throw new FsBridgeException("INSERT_FAILED",
                        "Salesforce returned failure for insert into " + objectType);
            }

            String newId = (String) responseBody.get("id");
            log.debug("Record inserted successfully. New ID: {}", newId);
            return newId;

        } catch (FsBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to insert record into {}: {}", objectType, e.getMessage());
            throw new FsBridgeException("INSERT_FAILED",
                    "Failed to insert record: " + e.getMessage(), e);
        }
    }

    /**
     * DELETES a record from Salesforce by ID.
     * Used by the rollback engine to undo a migration.
     *
     * @param objectType - Salesforce object API name
     * @param recordId   - the ID of the record to delete
     */
    public void deleteRecord(String objectType, String recordId) {
        log.info("Deleting record {} from {}", recordId, objectType);

        try {
            String deleteUrl = oAuthService.getInstanceUrl()
                    + "/services/data/"
                    + properties.getApiVersion()
                    + "/sobjects/"
                    + objectType
                    + "/"
                    + recordId;

            executeWithRetry(
                    deleteUrl,
                    HttpMethod.DELETE,
                    new HttpEntity<>(buildHeaders()),
                    Void.class
            );

            log.info("Record {} deleted successfully", recordId);

        } catch (Exception e) {
            log.error("Failed to delete record {}: {}", recordId, e.getMessage());
            throw new FsBridgeException("DELETE_FAILED",
                    "Failed to delete record " + recordId + ": " + e.getMessage(), e);
        }
    }

    /**
     * BUILDS HTTP HEADERS for every Salesforce API call.
     *
     * Every request needs:
     * 1. Authorization: Bearer YOUR_TOKEN  ← proves who we are
     * 2. Content-Type: application/json    ← we send/receive JSON
     *
     * This is a private helper method.
     * Private = only used inside this class.
     * Helper = just reduces code repetition.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + oAuthService.getAccessToken());
        return headers;
    }

    private <T> ResponseEntity<T> executeWithRetry(
            String url, HttpMethod method, HttpEntity<?> requestEntity,
            Class<T> responseType) {
        try {
            return restTemplate.exchange(url, method, requestEntity, responseType);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Received 401 from Salesforce. Refreshing token and retrying...");
                oAuthService.refreshToken();
                HttpHeaders freshHeaders = buildHeaders();
                Object body = requestEntity.getBody();
                HttpEntity<?> retryRequest = body != null
                        ? new HttpEntity<>(body, freshHeaders)
                        : new HttpEntity<>(freshHeaders);
                return restTemplate.exchange(url, method, retryRequest, responseType);
            }
            throw e;
        }
    }
}