package me.bombo.bomboaddons.auth;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
   private static final String ALGORITHM = "AES";
   private static SecretKey secretKey;

   public static void init(File keyFile) {
      try {
         if (keyFile.exists()) {
            byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
            secretKey = new SecretKeySpec(keyBytes, "AES");
         } else {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128, new SecureRandom());
            secretKey = keyGen.generateKey();
            Files.write(keyFile.toPath(), secretKey.getEncoded(), new OpenOption[0]);
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static String encrypt(String value) {
      if (secretKey != null && value != null) {
         try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(1, secretKey);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
         } catch (Exception e) {
            e.printStackTrace();
            return value;
         }
      } else {
         return value;
      }
   }

   public static String decrypt(String encryptedValue) {
      if (secretKey != null && encryptedValue != null) {
         try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(2, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted);
         } catch (Exception e) {
            e.printStackTrace();
            return encryptedValue;
         }
      } else {
         return encryptedValue;
      }
   }
}
