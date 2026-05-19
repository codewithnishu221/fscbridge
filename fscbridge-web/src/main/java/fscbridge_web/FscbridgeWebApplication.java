package fscbridge_web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "fscbridge_web",
        "fsbridge_connector",
        "fsbridge_mapper",
        "fscbridge_audit"
})
public class FscbridgeWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(FscbridgeWebApplication.class, args);

        System.out.println("\n==========================================");
        System.out.println("  FSC-Bridge is running!");
        System.out.println("  API:    http://localhost:8080/api/migration/health");
        System.out.println("  H2 DB:  http://localhost:8080/h2-console");
        System.out.println("  Health: http://localhost:8080/actuator/health");
        System.out.println("==========================================\n");
    }
}