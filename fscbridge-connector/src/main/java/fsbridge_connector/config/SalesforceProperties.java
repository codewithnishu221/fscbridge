package fsbridge_connector.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "salesforce")
public class SalesforceProperties {

    private String clientId;
    private String clientSecret;
    private String loginUrl;
    private String grantType;
//    private String username;
//    private String password;
    private String apiVersion = "v59.0";

}
