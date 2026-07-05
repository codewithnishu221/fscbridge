package fscbridge_mapper.service;

import fscbridge_mapper.config.MappingLoader;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.FieldMapping;
import fscbridge_core.model.SalesforceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldMapperService {

     private final MappingLoader mappingLoader;

     public SalesforceRecord mapRecord(SalesforceRecord sourceRecord, String targetObject){
         log.debug("Mapping record {} from {} to {}",
                 sourceRecord.getId(),
                 sourceRecord.getObjectType(),
                 targetObject);
         try{
             List<FieldMapping> mappingRules = mappingLoader.getMappings();
             Map<String, Object> sourceFields = sourceRecord.getFields();
             Map<String, Object> targetFields = new HashMap<>();

             for(Map.Entry<String, Object> sourceEntry : sourceFields.entrySet()){
                 String sourceFieldName = sourceEntry.getKey();
                 Object sourceValue = sourceEntry.getValue();

                 FieldMapping rule = findMappingRule(mappingRules, sourceFieldName);

                 if(rule == null){
                     String strippedName = stripNamespace(sourceFieldName);
                     targetFields.put(strippedName, sourceValue);
                     log.debug("No mapping rule for field'{}'. " +
                             "Stripped namespace and carried over as '{}'",
                             sourceFieldName, strippedName);
                     continue;
                 }
                 if(rule.isSkip()){
                     log.debug("Skipping field '{}' as per mapping rule. ", sourceFieldName);
                     continue;
                 }
                 Object finalValue = resolveValue(sourceValue, rule);

                 targetFields.put(rule.getTargetField(), finalValue);
                 log.debug("Mapped '{}' -> '{}' with value: {}", sourceFieldName, rule.getTargetField(), finalValue);
             }

             SalesforceRecord mappedRecord = SalesforceRecord.builder()
                     .id(sourceRecord.getId())
                     .objectType(targetObject)
                     .fields(targetFields)
                     .build();

             log.debug("Successfully mapped record {}. " + "Source had {} fields, target has {} fields. ",
                     sourceRecord.getId(),
                     sourceFields.size(),
                     targetFields.size());
             return mappedRecord;
         } catch (FsBridgeException e){
             throw e;
         } catch (Exception e) {
             log.error("Failed to map record {}: {}", sourceRecord.getId(), e.getMessage());
             throw new FsBridgeException("MAPPING_FAILED", "Failed to map record" + sourceRecord.getId()
             + ": " + e.getMessage(), e);
         }
     }
     public  String stripNamespace(String fieldName){
         if(fieldName == null) return "";
         String result = fieldName;
         if(result.startsWith("FinServ__")){
             result = result.substring("FinServ__".length());
             }

         if(result.endsWith("__c")){
             result = result.substring(0, result.length() - "__c".length());
         }
         return result;
     }

     private FieldMapping findMappingRule(List<FieldMapping> rules, String sourceField){
         return  rules.stream()
                 .filter(rule -> rule.getSourceField().equals(sourceField))
                 .findFirst().orElse(null);
     }

     private Object resolveValue(Object sourceValue, FieldMapping rule){
         if(sourceValue!= null){
             return sourceValue;
         }

         if(rule.getDefaultValue()!= null && ! rule.getDefaultValue().isEmpty()){
             log.debug("Field '{}' is null. Using default value: '{}'",
                     rule.getSourceField(), rule.getDefaultValue());
             return rule.getDefaultValue();
         }
         if(rule.isRequired()){
             throw new FsBridgeException("REQUIRED_FIELD_NULL", "Required field '" +
                     rule.getSourceField() +
                     "' is null and has no default value.");
         }
         return null;
     }
}
