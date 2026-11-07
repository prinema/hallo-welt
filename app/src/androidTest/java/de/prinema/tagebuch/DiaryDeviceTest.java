package de.prinema.tagebuch;

import android.content.Intent;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;
import org.junit.Test;
import androidx.test.uiautomator.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class DiaryDeviceTest {
    private UiDevice device;
    private static final String PKG="de.prinema.tagebuch";
    private void waitText(String text) { assertTrue("Missing UI: "+text, device.wait(Until.hasObject(By.textContains(text)),15000)); }
    private void tap(String text) { waitText(text);device.findObject(By.textContains(text)).click(); }
    private void launch() throws Exception {
        Intent i=getInstrumentation().getTargetContext().getPackageManager().getLaunchIntentForPackage(PKG);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getInstrumentation().getTargetContext().startActivity(i);waitText("Entsperren");
    }
    private void unlock() throws Exception {
        tap("Entsperren");
        // Emulator has no enrolled fingerprint, so Android opens the system PIN screen.
        assertTrue("System PIN screen missing",device.wait(Until.hasObject(By.clazz("android.widget.EditText")),15000));
        UiObject2 pin=device.findObject(By.clazz("android.widget.EditText"));pin.click();device.executeShellCommand("input text 2468");device.pressEnter();
        waitText("Neuer Eintrag");
    }
    @Test public void testEncryptedDiaryAndLocking() throws Exception {
        device=UiDevice.getInstance(getInstrumentation());launch();unlock();
        tap("Neuer Eintrag");device.findObject(By.clazz("android.widget.EditText")).setText("Ein sonniger Testtag mit Grüßen.");tap("Speichern");waitText("Ein sonniger Testtag");
        // A second entry on the same day must remain a separate entry.
        tap("Neuer Eintrag");device.findObject(By.clazz("android.widget.EditText")).setText("Noch ein Moment am selben Tag.");tap("Speichern");waitText("Noch ein Moment");
        File f=new File(getInstrumentation().getTargetContext().getNoBackupFilesDir(),"diary.vault");assertTrue(f.exists());
        byte[] stored;try(InputStream in=new FileInputStream(f)){stored=CryptoBox.readLimited(in);}
        assertFalse(new String(stored,StandardCharsets.UTF_8).contains("sonniger Testtag"));
        device.pressHome();launch();unlock();waitText("Ein sonniger Testtag");waitText("Noch ein Moment");
        tap("Ein sonniger Testtag");device.findObject(By.clazz("android.widget.EditText")).setText("Bearbeiteter Gedanke.");tap("Speichern");waitText("Bearbeiteter Gedanke");
        tap("Bearbeiteter Gedanke");tap("Löschen");
        assertTrue(device.wait(Until.hasObject(By.text("Eintrag löschen?")),5000));device.findObject(By.res("android:id/button1")).click();waitText("Noch ein Moment");
        assertFalse(device.hasObject(By.textContains("Bearbeiteter Gedanke")));
        tap("Sicherungen");waitText("Sicherung exportieren");waitText("Sicherung importieren");
        byte[] json=Vault.encode(java.util.List.of(new Vault.Entry(java.util.UUID.randomUUID().toString(),"Wiederherstellung 🌞",123,123)));
        byte[] backup=CryptoBox.backup(json,"Ein langes Sicherungspasswort".toCharArray());
        assertEquals("Wiederherstellung 🌞",Vault.decode(CryptoBox.restore(backup,"Ein langes Sicherungspasswort".toCharArray())).get(0).text);
    }
}
