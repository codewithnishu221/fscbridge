package fscbridge_web.validator;

import fscbridge_connector.auth.OAuthService;
import fscbridge_connector.client.SalesforceClient;
import fscbridge_core.model.FieldMapping;
import fscbridge_core.model.SalesforceRecord;
import fscbridge_mapper.config.MappingLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class PreFlightValidator {

    private final OAuthService oAuthService;
    private final SalesforceClient salesforceClient;
    private final MappingLoader mappingLoader;


    public ValidationReport validate(String sourceObject, String targetObject) {
        log.info("Starting pre-flight validation: {} → {}",
                sourceObject, targetObject);

        List<ValidationResult> checks = new ArrayList<>();
        int totalRecords = 0;

        ValidationResult connectionCheck = checkSourceConnection();
        checks.add(connectionCheck);

        if (connectionCheck.getStatus() ==
                ValidationResult.ValidationStatus.FAIL) {
            return buildReport(sourceObject, targetObject,
                    checks, 0);
        }

        ValidationResult objectCheck = checkObjectExists(sourceObject);
        checks.add(objectCheck);

        if (objectCheck.getStatus() !=
                ValidationResult.ValidationStatus.FAIL) {
            int count = getRecordCount(sourceObject);
            totalRecords = count;
            checks.add(buildRecordCountResult(sourceObject, count));
        }

        if (totalRecords > 0) {
            ValidationResult nullCheck = checkRequiredFields(
                    sourceObject, totalRecords);
            checks.add(nullCheck);
        }

        ValidationResult mappingCheck = checkMappingRules(sourceObject);
        checks.add(mappingCheck);

        ValidationResult apiCheck = checkApiEstimate(totalRecords);
        checks.add(apiCheck);

        return buildReport(sourceObject, targetObject, checks, totalRecords);
    }



    private ValidationResult checkSourceConnection() {
        log.debug("Running check: Source Org Connection");
        try {
            String token = oAuthService.getAccessToken();
            if (token != null && !token.isEmpty()) {
                return ValidationResult.pass(
                        "Source Org Connection",
                        "Successfully authenticated with Salesforce. " +
                                "Instance: " + oAuthService.getInstanceUrl()
                );
            } else {
                return ValidationResult.fail(
                        "Source Org Connection",
                        "Authentication returned empty token.",
                        "Check clientId and clientSecret in application.yml"
                );
            }
        } catch (Exception e) {
            return ValidationResult.fail(
                    "Source Org Connection",
                    "Failed to connect to Salesforce.",
                    e.getMessage()
            );
        }
    }


    private ValidationResult checkObjectExists(String sourceObject) {
        log.debug("Running check: Source Object Exists - {}", sourceObject);
        try {
            String testQuery = "SELECT Id FROM " + sourceObject + " LIMIT 1";
            salesforceClient.queryRecords(testQuery);
            return ValidationResult.pass(
                    "Source Object Exists",
                    "Object '" + sourceObject + "' found in source org."
            );
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail != null && detail.contains("INVALID_TYPE")) {
                return ValidationResult.fail(
                        "Source Object Exists",
                        "Object '" + sourceObject + "' does not exist in source org.",
                        "If this is a FSC object, ensure FSC is installed in your org."
                );
            }
            return ValidationResult.warn(
                    "Source Object Exists",
                    "Could not verify object '" + sourceObject + "'.",
                    detail
            );
        }
    }


    private int getRecordCount(String sourceObject) {
        log.debug("Running check: Record Count for {}", sourceObject);
        try {
            String countQuery = "SELECT COUNT() FROM " + sourceObject;
            return salesforceClient.countRecords(countQuery);
        } catch (Exception e) {
            log.warn("Could not get record count: {}", e.getMessage());
            return 0;
        }
    }

    private ValidationResult buildRecordCountResult(
            String sourceObject, int count) {

        if (count == 0) {
            return ValidationResult.warn(
                    "Record Count",
                    "No records found in '" + sourceObject + "'.",
                    "Migration will complete with 0 records processed."
            );
        } else if (count > 10000) {
            return ValidationResult.warn(
                    "Record Count",
                    count + " records found. Large migration detected.",
                    "Consider running in batches. " +
                            "Use /api/migration/batch-start endpoint."
            );
        } else {
            return ValidationResult.pass(
                    "Record Count",
                    count + " records found in '" + sourceObject + "'."
            );
        }
    }


    private ValidationResult checkRequiredFields(
            String sourceObject, int totalRecords) {

        log.debug("Running check: Required Fields Null Check");
        try {
            List<FieldMapping> mappings = mappingLoader.getMappings();
            List<FieldMapping> requiredMappings = mappings.stream()
                    .filter(FieldMapping::isRequired)
                    .filter(m -> !m.isSkip())
                    .toList();

            if (requiredMappings.isEmpty()) {
                return ValidationResult.pass(
                        "Required Fields Check",
                        "No required fields defined in mapping rules."
                );
            }

            String sampleQuery = "SELECT FIELDS(ALL) FROM "
                    + sourceObject + " LIMIT 10";
            List<SalesforceRecord> sampleRecords =
                    salesforceClient.queryRecords(sampleQuery);

            int nullCount = 0;
            List<String> nullFields = new ArrayList<>();

            for (SalesforceRecord record : sampleRecords) {
                for (FieldMapping required : requiredMappings) {
                    Object value = record.getFields()
                            .get(required.getSourceField());
                    if (value == null && required.getDefaultValue() == null) {
                        nullCount++;
                        if (!nullFields.contains(required.getSourceField())) {
                            nullFields.add(required.getSourceField());
                        }
                    }
                }
            }

            if (nullCount == 0) {
                return ValidationResult.pass(
                        "Required Fields Check",
                        "All required fields have values in sampled records."
                );
            } else {
                return ValidationResult.warn(
                        "Required Fields Check",
                        nullCount + " null values found in required fields " +
                                "across " + sampleRecords.size() + " sampled records.",
                        "Fields with nulls: " + String.join(", ", nullFields) +
                                ". These records will use default values if configured."
                );
            }

        } catch (Exception e) {
            return ValidationResult.warn(
                    "Required Fields Check",
                    "Could not complete required fields check.",
                    e.getMessage()
            );
        }
    }


    private ValidationResult checkMappingRules(String sourceObject) {
        log.debug("Running check: Mapping Rules Loaded");
        try {
            List<FieldMapping> mappings = mappingLoader.getMappings();

            if (mappings == null || mappings.isEmpty()) {
                return ValidationResult.fail(
                        "Mapping Rules",
                        "No mapping rules loaded from fsc-field-mappings.yml.",
                        "Check that the file exists in src/main/resources"
                );
            }

            long relevantRules = mappings.stream()
                    .filter(m -> m.getSourceField() != null &&
                            m.getSourceField().contains(
                                    sourceObject.replace(
                                                    "FinServ__", "")
                                            .replace("__c", "")))
                    .count();

            return ValidationResult.pass(
                    "Mapping Rules",
                    mappings.size() + " total mapping rules loaded. " +
                            relevantRules + " rules relevant to " + sourceObject + "."
            );

        } catch (Exception e) {
            return ValidationResult.fail(
                    "Mapping Rules",
                    "Failed to load mapping rules.",
                    e.getMessage()
            );
        }
    }


    private ValidationResult checkApiEstimate(int totalRecords) {
        log.debug("Running check: API Call Estimate");

        int estimated = 2 + (totalRecords / 200) + totalRecords;
        int devOrgLimit = 15000;

        if (estimated > devOrgLimit) {
            return ValidationResult.fail(
                    "API Call Estimate",
                    "Estimated " + estimated + " API calls exceeds " +
                            "Developer org limit of " + devOrgLimit + ".",
                    "Reduce record count or upgrade to Enterprise org."
            );
        } else if (estimated > devOrgLimit * 0.8) {
            return ValidationResult.warn(
                    "API Call Estimate",
                    "Estimated " + estimated + " API calls is close to " +
                            "Developer org limit of " + devOrgLimit + ".",
                    "Monitor your API usage in Salesforce Setup."
            );
        } else {
            return ValidationResult.pass(
                    "API Call Estimate",
                    "Estimated " + estimated + " API calls. " +
                            "Well within Developer org limit of " + devOrgLimit + "."
            );
        }
    }


    private ValidationReport buildReport(String sourceObject,
                                         String targetObject,
                                         List<ValidationResult> checks,
                                         int totalRecords) {

        long passCount = checks.stream()
                .filter(c -> c.getStatus() ==
                        ValidationResult.ValidationStatus.PASS)
                .count();
        long warnCount = checks.stream()
                .filter(c -> c.getStatus() ==
                        ValidationResult.ValidationStatus.WARN)
                .count();
        long failCount = checks.stream()
                .filter(c -> c.getStatus() ==
                        ValidationResult.ValidationStatus.FAIL)
                .count();

        ValidationResult.ValidationStatus overall;
        if (failCount > 0) {
            overall = ValidationResult.ValidationStatus.FAIL;
        } else if (warnCount > 0) {
            overall = ValidationResult.ValidationStatus.WARN;
        } else {
            overall = ValidationResult.ValidationStatus.PASS;
        }

        int estimatedApiCalls = 2 + (totalRecords / 200) + totalRecords;

        log.info("Validation complete: {} PASS, {} WARN, {} FAIL. Overall: {}",
                passCount, warnCount, failCount, overall);

        return ValidationReport.builder()
                .overallStatus(overall)
                .sourceObject(sourceObject)
                .targetObject(targetObject)
                .totalRecordsFound(totalRecords)
                .estimatedApiCalls(estimatedApiCalls)
                .checks(checks)
                .passCount(passCount)
                .warnCount(warnCount)
                .failCount(failCount)
                .validatedAt(LocalDateTime.now())
                .build();
    }
}