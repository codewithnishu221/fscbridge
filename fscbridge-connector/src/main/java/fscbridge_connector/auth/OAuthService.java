package fscbridge_connector.auth;

import fscbridge_connector.config.SalesforceProperties;
import fscbridge_core.exception.FsBridgeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
            log.info("No token found. Authenticating via Client Credentials...");
            authenticate();
        }
        return accessToken;
    }

    public String getInstanceUrl() {
        if (instanceUrl == null) {
            authenticate();
        }
        return instanceUrl;
    }

    public void refreshToken() {
        log.info("Refreshing Salesforce access token...");
        accessToken = null;
        instanceUrl = null;
        authenticate();
    }

    @SuppressWarnings("unchecked")
    private void authenticate() {
        try {
            // Validate orgUrl is not null before using it
            if (properties.getOrgUrl() == null
                    || properties.getOrgUrl().isBlank()) {
                throw new FsBridgeException("AUTH_FAILED",
                        "salesforce.orgUrl is not set in application.yml. " +
                                "Set it to your Salesforce My Domain URL. " +
                                "Example: https://yourcompany.my.salesforce.com");
            }

            // Build token endpoint from org URL
            String tokenUrl = properties.getOrgUrl().trim()
                    + "/services/oauth2/token";

            log.info("Authenticating with Salesforce: {}", tokenUrl);

            // Client credentials grant body
            MultiValueMap<String, String> requestBody =
                    new LinkedMultiValueMap<>();
            requestBody.add("grant_type", properties.getGrantType());
            requestBody.add("client_id", properties.getClientId());
            requestBody.add("client_secret", properties.getClientSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl, request, Map.class);

            Map<String, String> responseBody = response.getBody();

            if (responseBody == null) {
                throw new FsBridgeException("AUTH_FAILED",
                        "Empty response from Salesforce token endpoint");
            }

            this.accessToken = responseBody.get("access_token");
            this.instanceUrl = responseBody.get("instance_url");

            // Validate we actually got a token back
            if (this.accessToken == null || this.accessToken.isBlank()) {
                throw new FsBridgeException("AUTH_FAILED",
                        "Salesforce returned null access_token. " +
                                "Check clientId and clientSecret are correct. " +
                                "Also verify External Client App has " +
                                "Client Credentials flow enabled in Salesforce.");
            }

            log.info("Authentication successful. Instance: {}",
                    instanceUrl);

        } catch (FsBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication failed: {}", e.getMessage());
            throw new FsBridgeException("AUTH_FAILED",
                    "Could not authenticate with Salesforce: "
                            + e.getMessage(), e);
        }
    }
}