package fscbridge_web.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

public class JasyptEncryptionUtil {

    public static void main(String[] args) {

        String masterPassword = System.getProperty("jasypt.encryptor.password",
                System.getenv("JASYPT_PASSWORD"));
        if (masterPassword == null || masterPassword.isBlank()) {
            System.err.println("ERROR: Set Jasypt password via:");
            System.err.println("  -Djava.ext.dirs= -Djasypt.encryptor.password=your-password");
            System.err.println("  or JASYPT_PASSWORD environment variable");
            System.exit(1);
            return;
        }

        if (args.length < 1) {
            System.err.println("Usage: java JasyptEncryptionUtil <value1> [value2 ...]");
            System.err.println("  Encrypts one or more values and prints ENC(...) output.");
            System.exit(1);
            return;
        }

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(masterPassword);
        encryptor.setAlgorithm("PBEWithMD5AndDES");

        System.out.println("\n=== Encrypted values (copy into application.yml) ===");
        for (int i = 0; i < args.length; i++) {
            String encrypted = encryptor.encrypt(args[i]);
            System.out.printf("  arg%d: ENC(%s)%n", i + 1, encrypted);
        }
        System.out.println("==================================================\n");
        System.out.println("Start app with: -Djasypt.encryptor.password=<your-password>");
    }
}