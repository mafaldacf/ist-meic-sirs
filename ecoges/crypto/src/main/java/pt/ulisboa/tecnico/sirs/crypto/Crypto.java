package pt.ulisboa.tecnico.sirs.crypto;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Crypto {

    public static String hash(String message) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] bytes = digest.digest( message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);

    }

    public static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[20];
        random.nextBytes(bytes);
        return java.time.LocalDate.now() + bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xff & bytes[i]);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
