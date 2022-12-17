package pt.ulisboa.tecnico.sirs.crypto;

import com.google.protobuf.ByteString;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Crypto {

    public static String hash(String message) throws NoSuchAlgorithmException  {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] bytes = digest.digest(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);

    }

    public static String generateToken() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return java.time.LocalDate.now() + bytesToHex(bytes);
    }

    public static String hashWithSalt(String message, byte[] salt) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (salt != null && salt.length != 0) {
            digest.update(salt);
        }
        final byte[] bytes = digest.digest(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);
    }

    public static byte[] generateSalt() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return bytes;
    }

    public static byte[] randomBytes(int n) throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static byte[] wrapKey(PublicKey publicKey, Key secretKey) throws InvalidKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.WRAP_MODE, publicKey);
        final byte[] wrapped = cipher.wrap(secretKey);
        return wrapped;
    }

    public static Key unwrapKey(PublicKey publicKey, byte[] wrappedSecretKey) throws InvalidKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.UNWRAP_MODE, publicKey);
        Key secretKey = cipher.unwrap(wrappedSecretKey, "AES", Cipher.SECRET_KEY);
        return secretKey;
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        return keyGen.generateKeyPair();
    }
    public static ByteString getEncodedKey(Key key) {
        return ByteString.copyFrom(key.getEncoded());
    }

    public static PublicKey getPublicKey(ByteString encodedKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey.toByteArray());
        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKey(ByteString encodedKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey.toByteArray());
        return keyFactory.generatePrivate(keySpec);
    }

    /* Utils for Signatures with SHA256 */

    public static ByteString signMessage(PrivateKey key, byte[] message) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(message);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(digest);
        return ByteString.copyFrom(signer.sign());
    }

    public static boolean verifySignature(PublicKey key, byte[] signedMessage, byte[] message) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(message);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initVerify(key);
        signer.update(digest);

        return signer.verify(signedMessage);
    }


}
