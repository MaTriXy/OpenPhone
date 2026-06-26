package org.openphone.assistant.external;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

final class OpenClawEd25519 {
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.valueOf(2L);
    private static final BigInteger P = TWO.pow(255).subtract(BigInteger.valueOf(19L));
    private static final BigInteger L = TWO.pow(252).add(new BigInteger(
            "27742317777372353535851937790883648493"));
    private static final BigInteger D = BigInteger.valueOf(-121665L)
            .multiply(BigInteger.valueOf(121666L).modInverse(P)).mod(P);
    private static final Point BASE_POINT = new Point(
            new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"),
            new BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960"));
    private static final Point IDENTITY = new Point(ZERO, ONE);

    private OpenClawEd25519() {}

    static KeyPairData generate(SecureRandom random) {
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        return fromSeed(seed);
    }

    static KeyPairData fromSeed(byte[] seed) {
        byte[] cleanSeed = requireSeed(seed);
        return new KeyPairData(cleanSeed, publicKeyFromSeed(cleanSeed));
    }

    static byte[] sign(byte[] message, byte[] seed) {
        byte[] cleanSeed = requireSeed(seed);
        byte[] msg = message == null ? new byte[0] : message;
        byte[] digest = sha512(cleanSeed);
        byte[] scalarBytes = Arrays.copyOfRange(digest, 0, 32);
        clamp(scalarBytes);
        BigInteger scalar = littleEndianToInteger(scalarBytes);
        byte[] prefix = Arrays.copyOfRange(digest, 32, 64);
        byte[] publicKey = publicKeyFromSeed(cleanSeed);

        BigInteger r = littleEndianToInteger(sha512(prefix, msg)).mod(L);
        byte[] encodedR = encodePoint(scalarMultiply(BASE_POINT, r));
        BigInteger k = littleEndianToInteger(sha512(encodedR, publicKey, msg)).mod(L);
        BigInteger s = r.add(k.multiply(scalar)).mod(L);

        byte[] signature = new byte[64];
        System.arraycopy(encodedR, 0, signature, 0, 32);
        System.arraycopy(integerToLittleEndian(s, 32), 0, signature, 32, 32);
        return signature;
    }

    static boolean isValidSeed(byte[] seed) {
        return seed != null && seed.length == 32;
    }

    private static byte[] publicKeyFromSeed(byte[] seed) {
        byte[] digest = sha512(seed);
        byte[] scalarBytes = Arrays.copyOfRange(digest, 0, 32);
        clamp(scalarBytes);
        return encodePoint(scalarMultiply(BASE_POINT, littleEndianToInteger(scalarBytes)));
    }

    private static Point scalarMultiply(Point point, BigInteger scalar) {
        Point result = IDENTITY;
        Point addend = point;
        BigInteger value = scalar;
        while (value.signum() > 0) {
            if (value.testBit(0)) {
                result = add(result, addend);
            }
            addend = add(addend, addend);
            value = value.shiftRight(1);
        }
        return result;
    }

    private static Point add(Point a, Point b) {
        BigInteger x1x2 = a.x.multiply(b.x).mod(P);
        BigInteger y1y2 = a.y.multiply(b.y).mod(P);
        BigInteger x1y2 = a.x.multiply(b.y).mod(P);
        BigInteger x2y1 = b.x.multiply(a.y).mod(P);
        BigInteger dxy = D.multiply(x1x2).mod(P).multiply(y1y2).mod(P);
        BigInteger x = x1y2.add(x2y1).mod(P)
                .multiply(ONE.add(dxy).mod(P).modInverse(P)).mod(P);
        BigInteger y = y1y2.add(x1x2).mod(P)
                .multiply(ONE.subtract(dxy).mod(P).modInverse(P)).mod(P);
        return new Point(x, y);
    }

    private static byte[] encodePoint(Point point) {
        byte[] encoded = integerToLittleEndian(point.y, 32);
        if (point.x.testBit(0)) {
            encoded[31] = (byte) (encoded[31] | 0x80);
        }
        return encoded;
    }

    private static byte[] requireSeed(byte[] seed) {
        if (!isValidSeed(seed)) {
            throw new IllegalArgumentException("Ed25519 seed must be 32 bytes");
        }
        return Arrays.copyOf(seed, seed.length);
    }

    private static void clamp(byte[] scalar) {
        scalar[0] = (byte) (scalar[0] & 0xf8);
        scalar[31] = (byte) (scalar[31] & 0x3f);
        scalar[31] = (byte) (scalar[31] | 0x40);
    }

    private static BigInteger littleEndianToInteger(byte[] value) {
        byte[] bigEndian = new byte[value.length];
        for (int i = 0; i < value.length; i++) {
            bigEndian[value.length - 1 - i] = value[i];
        }
        return new BigInteger(1, bigEndian);
    }

    private static byte[] integerToLittleEndian(BigInteger value, int length) {
        byte[] bigEndian = value.mod(TWO.pow(length * 8)).toByteArray();
        byte[] out = new byte[length];
        for (int i = 0; i < bigEndian.length; i++) {
            int source = bigEndian.length - 1 - i;
            if (i < out.length) {
                out[i] = bigEndian[source];
            }
        }
        return out;
    }

    private static byte[] sha512(byte[]... chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    digest.update(chunk);
                }
            }
            return digest.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 unavailable", e);
        }
    }

    static final class KeyPairData {
        final byte[] seed;
        final byte[] publicKey;

        KeyPairData(byte[] seed, byte[] publicKey) {
            this.seed = Arrays.copyOf(seed, seed.length);
            this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        }
    }

    private static final class Point {
        final BigInteger x;
        final BigInteger y;

        Point(BigInteger x, BigInteger y) {
            this.x = x.mod(P);
            this.y = y.mod(P);
        }
    }
}
