package de.prinema.tagebuch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Versioned, authenticated envelopes. No plaintext files, passwords or keys. */
public final class CryptoBox {
    public static final int MAX_FILE = 8 * 1024 * 1024;
    private static final byte[] BACKUP = "TAGEBUCH-BACKUP-1".getBytes(StandardCharsets.US_ASCII);
    public static byte[] random(int count) { byte[] b = new byte[count]; new SecureRandom().nextBytes(b); return b; }
    public static byte[] seal(byte[] key, byte[] plain, byte[] aad) throws Exception {
        byte[] nonce = random(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        c.updateAAD(aad);
        byte[] encrypted = c.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(nonce); out.write(encrypted); return out.toByteArray();
    }
    public static byte[] open(byte[] key, byte[] sealed, byte[] aad) throws Exception {
        if (sealed.length < 28 || sealed.length > MAX_FILE) throw new IOException("Invalid envelope");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Arrays.copyOf(sealed, 12)));
        c.updateAAD(aad); return c.doFinal(sealed, 12, sealed.length - 12);
    }
    private static byte[] derive(char[] password, byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(65536).withIterations(3).withParallelism(1).withSalt(salt).build();
        try {
            Argon2BytesGenerator generator = new Argon2BytesGenerator(); generator.init(params);
            byte[] key = new byte[32]; generator.generateBytes(password, key); return key;
        } finally { params.clear(); }
    }
    public static byte[] backup(byte[] plain, char[] password) throws Exception {
        byte[] salt = random(16), key = derive(password, salt);
        try {
            ByteArrayOutputStream header = new ByteArrayOutputStream(); header.write(BACKUP); header.write(salt);
            byte[] aad = header.toByteArray(); header.write(seal(key, plain, aad)); return header.toByteArray();
        } finally { Arrays.fill(key, (byte)0); }
    }
    public static byte[] restore(byte[] file, char[] password) throws Exception {
        int n = BACKUP.length + 16;
        if (file.length < n + 28 || file.length > MAX_FILE || !Arrays.equals(BACKUP, Arrays.copyOf(file, BACKUP.length))) throw new IOException("Invalid backup");
        byte[] key = derive(password, Arrays.copyOfRange(file, BACKUP.length, n));
        try { return open(key, Arrays.copyOfRange(file, n, file.length), Arrays.copyOf(file, n)); }
        finally { Arrays.fill(key, (byte)0); }
    }
    public static byte[] readLimited(InputStream in) throws IOException {
        if (in == null) throw new IOException("No stream");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) { if (out.size() + n > MAX_FILE) throw new IOException("Too large"); out.write(buffer, 0, n); }
        Arrays.fill(buffer, (byte)0); return out.toByteArray();
    }
}
