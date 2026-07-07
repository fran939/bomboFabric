package me.bombo.bomboaddons.auth;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    private static SecretKey secretKey;

    public static void init(File keyFile) {
        try {
            if (keyFile.exists()) {
                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            } else {
                KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
                keyGen.init(128, new SecureRandom());
                secretKey = keyGen.generateKey();
                Files.write(keyFile.toPath(), secretKey.getEncoded());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String encrypt(String value) {
        if (secretKey == null || value == null) return value;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String decrypt(String encryptedValue) {
        if (secretKey == null || encryptedValue == null) return encryptedValue;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encryptedValue;
    }
}
