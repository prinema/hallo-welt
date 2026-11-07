package de.prinema.tagebuch;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CryptoBoxTest {
    private final byte[] plain="Mein Tagebuch: Grüße 🌞 – 7. November, 18:42".getBytes(StandardCharsets.UTF_8);
    private final byte[] aad="metadata-v1".getBytes(StandardCharsets.UTF_8);
    @Test public void localRoundtripAndUniqueNonces() throws Exception {
        byte[] key=CryptoBox.random(32),a=CryptoBox.seal(key,plain,aad),b=CryptoBox.seal(key,plain,aad);
        assertArrayEquals(plain,CryptoBox.open(key,a,aad));assertFalse(Arrays.equals(a,b));
        assertFalse(new String(a,StandardCharsets.UTF_8).contains("Mein Tagebuch"));
    }
    @Test public void alteredCiphertextAndHeaderAreRejected() throws Exception {
        byte[] key=CryptoBox.random(32),file=CryptoBox.seal(key,plain,aad);file[file.length-1]^=1;
        assertThrows(Exception.class,()->CryptoBox.open(key,file,aad));
        byte[] valid=CryptoBox.seal(key,plain,aad);
        assertThrows(Exception.class,()->CryptoBox.open(key,valid,new byte[]{1}));
        assertThrows(Exception.class,()->CryptoBox.open(CryptoBox.random(32),valid,aad));
    }
    @Test public void passwordBackupRoundtripAndWrongPassword() throws Exception {
        byte[] file=CryptoBox.backup(plain,"ein langes Testpasswort 🌞".toCharArray());
        assertArrayEquals(plain,CryptoBox.restore(file,"ein langes Testpasswort 🌞".toCharArray()));
        assertThrows(Exception.class,()->CryptoBox.restore(file,"falsches Passwort".toCharArray()));
        file[file.length-1]^=1;
        assertThrows(Exception.class,()->CryptoBox.restore(file,"ein langes Testpasswort 🌞".toCharArray()));
    }
    @Test public void truncatedAndOversizedFilesAreRejected() {
        assertThrows(Exception.class,()->CryptoBox.restore(new byte[0],"pass".toCharArray()));
        assertThrows(Exception.class,()->CryptoBox.readLimited(new java.io.ByteArrayInputStream(new byte[CryptoBox.MAX_FILE+1])));
    }
}
