package fscbridge_web.config;

import fscbridge_connector.config.SalesforceProperties;
import fscbridge_connector.config.RestTemplateConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "fscbridge_audit.model"
})
@EnableJpaRepositories(basePackages = {
        "fscbridge_audit.repository"
})
@EnableConfigurationProperties(SalesforceProperties.class)
@Import({RestTemplateConfig.class})
public class AppConfig {

}