package fscbridge_web.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@Configuration
@EntityScan(basePackages = {
        "fscbridge_audit.model"
})
@EnableJpaRepositories(basePackages = {
        "fscbridge_audit.repository"
})
public class AppConfig {

}