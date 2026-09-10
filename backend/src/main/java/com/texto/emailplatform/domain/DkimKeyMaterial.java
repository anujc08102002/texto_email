package com.texto.emailplatform.domain;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

final class DkimKeyMaterial {

    static final String PRIVATE_KEY_PREFIX = "pkcs8:";

    private DkimKeyMaterial() {
    }

    static Generated generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            String publicPkcs1 = Base64.getEncoder().encodeToString(pkcs1(publicKey));
            String privatePkcs8 = PRIVATE_KEY_PREFIX + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return new Generated(publicPkcs1, privatePkcs8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate DKIM key pair", exception);
        }
    }

    static boolean isStoredPrivateKey(String value) {
        return value != null && value.startsWith(PRIVATE_KEY_PREFIX);
    }

    static boolean keyPairMatches(String storedPublicKey, String storedPrivateKey) {
        if (storedPublicKey == null || !isStoredPrivateKey(storedPrivateKey)) {
            return false;
        }
        try {
            PrivateKey privateKey = loadPrivateKey(storedPrivateKey);
            if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
                return false;
            }
            RSAPublicKey derived = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            String derivedPkcs1 = Base64.getEncoder().encodeToString(pkcs1(derived));
            return storedPublicKey.replaceAll("\\s+", "").equals(derivedPkcs1);
        } catch (Exception exception) {
            return false;
        }
    }

    static PrivateKey loadPrivateKey(String stored) {
        if (!isStoredPrivateKey(stored)) {
            throw new IllegalStateException("DKIM private key is missing");
        }
        try {
            byte[] der = Base64.getDecoder().decode(stored.substring(PRIVATE_KEY_PREFIX.length()));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load DKIM private key", exception);
        }
    }

    static byte[] pkcs1(RSAPublicKey publicKey) {
        byte[] modulus = unsigned(publicKey.getModulus());
        byte[] exponent = unsigned(publicKey.getPublicExponent());
        return derSequence(derInteger(modulus), derInteger(exponent));
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] withoutSign = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, withoutSign, 0, withoutSign.length);
            return withoutSign;
        }
        return bytes;
    }

    private static byte[] derInteger(byte[] value) {
        boolean pad = (value[0] & 0x80) != 0;
        byte[] content = pad ? concat(new byte[] {0}, value) : value;
        return concat(new byte[] {0x02}, encodeLength(content.length), content);
    }

    private static byte[] derSequence(byte[]... parts) {
        byte[] content = concat(parts);
        return concat(new byte[] {0x30}, encodeLength(content.length), content);
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[] {(byte) length};
        }
        if (length <= 0xFF) {
            return new byte[] {(byte) 0x81, (byte) length};
        }
        return new byte[] {(byte) 0x82, (byte) ((length >> 8) & 0xFF), (byte) (length & 0xFF)};
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }

    record Generated(String publicKeyPkcs1, String privateKeyPkcs8) {
    }
}
