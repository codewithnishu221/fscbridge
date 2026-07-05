package fscbridge_mapper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import fscbridge_core.exception.FsBridgeException;
import fscbridge_core.model.FieldMapping;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Component
public class MappingLoader {

      private List<FieldMapping> mappings;

      @PostConstruct
      public void loadMappings(){
          log.info("Loading FSC field mappings from fsc-field-mappings.yml...");
          try{
              ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
              ClassPathResource resource = new ClassPathResource("fsc-field-mappings.yml");
              InputStream inputStream = resource.getInputStream();
              Map<String, Object> yamlContent = yamlMapper.readValue(
                      inputStream, Map.class
              );
              Object  mappingsList = yamlContent.get("mappings");
              mappings = yamlMapper.convertValue(
                      mappingsList,
                      yamlMapper.getTypeFactory().constructCollectionType(
                              List.class,
                              FieldMapping.class
                      )
              );
              log.info("Successfully loaded {} field mapping rules.", mappings.size());
              } catch (Exception e) {
              log.error("Failed to load field mappings: {}", e.getMessage());
              throw new FsBridgeException("MAPPING_LOAD_FAILED",
                      "Could not load fsc-field-mappings.yml: "+ e.getMessage(), e);
          }
      }
}
