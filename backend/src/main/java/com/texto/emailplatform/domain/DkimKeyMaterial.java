package com.texto.emailplatform.domain;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

public final class DkimKeyMaterial {

    public static final String PRIVATE_KEY_PREFIX = "pkcs8:";
    public static final int RSA_KEY_SIZE = 2048;

    private DkimKeyMaterial() {
    }

    public static Generated generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            String publicPkcs1 = Base64.getEncoder().encodeToString(pkcs1(publicKey));
            String privatePkcs8 = PRIVATE_KEY_PREFIX + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return new Generated(publicPkcs1, privatePkcs8, keyPair.getPrivate().getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate DKIM key pair", exception);
        }
    }

    public static boolean isStoredPrivateKey(String value) {
        return value != null && value.startsWith(PRIVATE_KEY_PREFIX);
    }

    public static boolean keyPairMatches(String storedPublicKey, String storedPrivateKey) {
        if (storedPublicKey == null || !isStoredPrivateKey(storedPrivateKey)) {
            return false;
        }
        try {
            return publicMatches(storedPublicKey, loadPrivateKey(storedPrivateKey));
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean publicMatches(String storedPublicKey, PrivateKey privateKey) {
        if (storedPublicKey == null || !(privateKey instanceof RSAPrivateCrtKey crt)) {
            return false;
        }
        try {
            RSAPublicKey derived = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            String derivedPkcs1 = Base64.getEncoder().encodeToString(pkcs1(derived));
            return storedPublicKey.replaceAll("\\s+", "").equals(derivedPkcs1);
        } catch (Exception exception) {
            return false;
        }
    }

    public static PrivateKey loadPrivateKey(String stored) {
        if (!isStoredPrivateKey(stored)) {
            throw new IllegalStateException("DKIM private key is missing");
        }
        return loadPkcs8(Base64.getDecoder().decode(stored.substring(PRIVATE_KEY_PREFIX.length())));
    }

    public static PrivateKey loadPkcs8(byte[] der) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load DKIM private key");
        }
    }

    public static byte[] pkcs8Der(String storedPrivateKey) {
        if (!isStoredPrivateKey(storedPrivateKey)) {
            throw new IllegalStateException("DKIM private key is missing");
        }
        return Base64.getDecoder().decode(storedPrivateKey.substring(PRIVATE_KEY_PREFIX.length()));
    }

    public static PublicKey publicKeyFromPkcs1(String storedPublicKey) {
        if (storedPublicKey == null || storedPublicKey.isBlank()) {
            throw new IllegalStateException("DKIM public key is missing");
        }
        try {
            byte[] der = Base64.getDecoder().decode(storedPublicKey.replaceAll("\\s+", ""));
            DerCursor cursor = new DerCursor(der);
            byte[] sequence = cursor.readSequence();
            DerCursor inner = new DerCursor(sequence);
            BigInteger modulus = inner.readInteger();
            BigInteger exponent = inner.readInteger();
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse DKIM public key");
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

    private static final class DerCursor {
        private final byte[] data;
        private int offset;

        private DerCursor(byte[] data) {
            this.data = data;
        }

        private byte[] readSequence() {
            if (offset >= data.length || data[offset++] != 0x30) {
                throw new IllegalStateException("Unable to parse DKIM public key");
            }
            int length = readLength();
            byte[] content = copy(data, offset, length);
            offset += length;
            return content;
        }

        private BigInteger readInteger() {
            if (offset >= data.length || data[offset++] != 0x02) {
                throw new IllegalStateException("Unable to parse DKIM public key");
            }
            int length = readLength();
            byte[] content = copy(data, offset, length);
            offset += length;
            return new BigInteger(content);
        }

        private int readLength() {
            if (offset >= data.length) {
                throw new IllegalStateException("Unable to parse DKIM public key");
            }
            int first = data[offset++] & 0xFF;
            if (first < 128) {
                return first;
            }
            int count = first & 0x7F;
            int length = 0;
            for (int i = 0; i < count; i++) {
                if (offset >= data.length) {
                    throw new IllegalStateException("Unable to parse DKIM public key");
                }
                length = (length << 8) | (data[offset++] & 0xFF);
            }
            return length;
        }
    }

    private static byte[] copy(byte[] source, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > source.length) {
            throw new IllegalStateException("Unable to parse DKIM public key");
        }
        byte[] copied = new byte[length];
        System.arraycopy(source, offset, copied, 0, length);
        return copied;
    }

    public record Generated(String publicKeyPkcs1, String privateKeyPkcs8, byte[] pkcs8Der) {
        @Override
        public String toString() {
            return "Generated[publicKeyPkcs1=***]";
        }
    }
}
