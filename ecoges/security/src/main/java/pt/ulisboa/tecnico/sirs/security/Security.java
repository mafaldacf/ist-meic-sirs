package pt.ulisboa.tecnico.sirs.security;

import com.google.protobuf.ByteString;
import pt.ulisboa.tecnico.sirs.security.exceptions.WeakPasswordException;

import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Security {

    public static boolean verifyStrongPassword(String password) throws WeakPasswordException {
        String regex = "" +
                "^" + // start of line
                "(?=.*[0-9])" + // at least one digit
                "(?=.*[a-z])" + // at least one lowercase
                "(?=.*[A-Z])" + // at least one uppercase
                "(?=.*[!@#&()–[{}]:;',?/*~$^+=<>])" + // at least one special character
                "." + // matches anything
                "{10,30}" + // length of 10 to 30 characters
                "$" + // end of line
                "";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(password);

        if (!matcher.matches()) {
            throw new WeakPasswordException();
        }

        return true;
    }

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
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.WRAP_MODE, publicKey);
        return cipher.wrap(secretKey);
    }

    public static SecretKey unwrapKey(PrivateKey privateKey, byte[] wrappedSecretKey) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.UNWRAP_MODE, privateKey);
        return (SecretKey) cipher.unwrap(wrappedSecretKey, "AES", Cipher.SECRET_KEY);
    }

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

    public static void validateCertificateChain(X509Certificate certificate, X509Certificate CACertificate) throws CertificateException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, CertPathValidatorException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        CertPath cp = cf.generateCertPath(Collections.singletonList((certificate)));
        Set<TrustAnchor> trustAnchors = Set.of(new TrustAnchor(CACertificate, null));

        // Create a PKIXParameters object with the trust anchors
        PKIXParameters params = new PKIXParameters(trustAnchors);

        // Disable certificate revocation checking
        params.setRevocationEnabled(false);

        // Create a CertPathValidator object
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");

        // Validate the certificate chain
        cpv.validate(cp, params);
    }


}
