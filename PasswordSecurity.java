import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class PasswordSecurity {

    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static byte[] deriveKeyBytes(char[] masterPassword, String saltStr) {
        if (masterPassword == null || saltStr == null || saltStr.isEmpty()) {
            return null;
        }

        byte[] salt = null;
        PBEKeySpec spec = null;

        try {
            salt = Base64.decode(saltStr, Base64.NO_WRAP);

            if (salt.length != 16) return null;

            spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } catch (Exception e) {
            return null;
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }

            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
            }
        }
    }

    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static SecretKey deriveAesKey(char[] masterPassword, String saltStr) {
        byte[] keyBytes = deriveKeyBytes(masterPassword, saltStr);

        if (keyBytes == null) return null;

        try {
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public static String deriveDbPassphrase(char[] masterPassword, String saltStr) {
        if (saltStr == null || saltStr.isEmpty()) return null;
        try {
            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
            if (salt.length == 0) return null;
            KeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null; 
        }
    }

    private static SecretKey deriveKeyInternal(char[] masterPassword, String saltStr) {
        try {
            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
            if (salt.length == 0) return null;
            KeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    public static String encryptAES(char[] plainChars, SecretKey secretKey) {
        if (secretKey == null || plainChars == null) return null;
        byte[] utf8Bytes = null;
        try {
            ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plainChars));
            utf8Bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(utf8Bytes);
            if (byteBuffer.hasArray()) {
                Arrays.fill(byteBuffer.array(), (byte) 0);
            }

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(utf8Bytes);

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } finally {
            if (utf8Bytes != null) {
                Arrays.fill(utf8Bytes, (byte) 0);
            }
        }
    }

    public static String encryptAES(String plainText, SecretKey secretKey) {
        if (plainText == null) return null;
        char[] chars = plainText.toCharArray();
        try {
            return encryptAES(chars, secretKey);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public static String decryptAES(String encryptedData, SecretKey secretKey) {
        if (secretKey == null || encryptedData == null) return null;

        byte[] combined = null;
        byte[] iv = null;
        byte[] cipherText = null;
        byte[] plainText = null;

        try {
            combined = Base64.decode(encryptedData, Base64.NO_WRAP);

            if (combined.length < IV_LENGTH + GCM_TAG_LENGTH / 8) {
                return null;
            }

            iv = new byte[IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            cipherText = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return null;
        } finally {
            if (combined != null) {
              Arrays.fill(combined, (byte) 0);
            }
            if (iv != null) {
              Arrays.fill(iv, (byte) 0);
            }
            if (cipherText != null) {
              Arrays.fill(cipherText, (byte) 0);
            }
            if (plainText != null) {
              Arrays.fill(plainText, (byte) 0);
            }
        }
    }
}
