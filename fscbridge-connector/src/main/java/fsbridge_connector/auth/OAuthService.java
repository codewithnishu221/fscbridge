package fsbridge_connector.auth;


import fsbridge_connector.config.SalesforceProperties;
import fscbridge_core.exception.FsBridgeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.*;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final SalesforceProperties properties;
    private final RestTemplate restTemplate;
    private String accessToken;
    private String instanceUrl;

    public String getAccessToken() {
        if (accessToken == null) {
            log.info("No access token found. Authenticating with Salesforce...");
            authenticate();
        }
        return accessToken;
    }

    public String getInstanceUrl(){
        if(instanceUrl == null){
            authenticate();
        }
        return instanceUrl;
    }

    public void refreshToken(){
       log.info("Refreshing Salesforce access token...");
       accessToken = null;
       instanceUrl = null;
       authenticate();
    }


    @SuppressWarnings("unchecked")
    private void authenticate(){
        try{
            String tokenUrl = properties.getLoginUrl() + "/services/oauth2/token";
            log.debug("Calling Salesforce OAuth endpoint: {}", tokenUrl);
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "client_credentials");
            requestBody.add("client_id", properties.getClientId());
            requestBody.add("client_secret", properties.getClientSecret());
//            requestBody.add("userName", properties.getUsername());
//            requestBody.add("password", properties.getPassword());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    Map.class
            );
            Map<String, String> responseBody = response.getBody();
            if(responseBody == null){
                throw new FsBridgeException("AUTH_FAILED", "Empty response from Salesforce login");
            }

            this.accessToken = responseBody.get("access_token");
            this.instanceUrl = responseBody.get("instance_url");
            log.info("Successfully authenticated with Salesforce.");
            log.info("Instance URL: {}", instanceUrl);
        } catch (FsBridgeException e){
            throw e;
        } catch (Exception e) {
            log.error("Failed to authenticate with saleforce: {}", e.getMessage());
            throw new FsBridgeException("AUTH_FAILED",
                    "Could not authenticate with salesforce: " + e.getMessage(), e);
        }

    }
}
