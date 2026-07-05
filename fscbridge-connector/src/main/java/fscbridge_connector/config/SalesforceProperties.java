package fscbridge_connector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "salesforce")
public class SalesforceProperties {

    private String orgUrl;

    private String clientId;

    private String clientSecret;
    private String grantType;

    private String apiVersion = "v59.0";
}