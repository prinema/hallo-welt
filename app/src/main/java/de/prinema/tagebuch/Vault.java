package de.prinema.tagebuch;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
    private static final String ALIAS = "tagebuch-v1";
    private static final byte[] WRAP_AAD = "tagebuch-key-v1".getBytes(StandardCharsets.US_ASCII);
    private final AtomicFile file;
    private byte[] key, wrapped, opening;
    private boolean existing;
    static final class Entry {
        String id, text; long created, updated;
        Entry(String id, String text, long created, long updated) { this.id=id; this.text=text; this.created=created; this.updated=updated; }
    }
    Vault(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "diary.vault")); }
    private SecretKey wrappingKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            if (file.getBaseFile().exists()) throw new IOException("Key unavailable");
            KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG|KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .build());
            return kg.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS, null);
    }
    Cipher prepare() throws Exception {
        SecretKey kek=wrappingKey(); existing=file.getBaseFile().exists();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        if (existing) {
            try(InputStream in=file.openRead()) { opening=CryptoBox.readLimited(in); }
            if(opening.length<96 || opening[0]!='T'||opening[1]!='B'||opening[2]!=1||opening[3]!=0) throw new IOException("Invalid vault");
            wrapped=Arrays.copyOfRange(opening,4,64);
            c.init(Cipher.DECRYPT_MODE,kek,new GCMParameterSpec(128,Arrays.copyOf(wrapped,12)));
        } else c.init(Cipher.ENCRYPT_MODE,kek);
        return c;
    }
    ArrayList<Entry> finish(Cipher c) throws Exception {
        try {
            // Keystore updates must only run after BiometricPrompt authorizes this operation.
            c.updateAAD(WRAP_AAD);
            if(existing) {
                key=c.doFinal(wrapped,12,48);
                byte[] plain=CryptoBox.open(key,Arrays.copyOfRange(opening,64,opening.length),Arrays.copyOf(opening,64));
                try { return decode(plain); } finally { Arrays.fill(plain,(byte)0); }
            }
            key=CryptoBox.random(32);
            byte[] iv=c.getIV(); byte[] sealed=c.doFinal(key); wrapped=new byte[60];
            System.arraycopy(iv,0,wrapped,0,12); System.arraycopy(sealed,0,wrapped,12,48);
            ArrayList<Entry> entries=new ArrayList<>(); save(entries); return entries;
        } catch(Exception e) { lock(); throw e; }
        finally { opening=null; }
    }
    void save(List<Entry> entries) throws Exception {
        if(key==null) throw new IOException("Locked");
        if(entries.size()>20000) throw new IOException("Too many entries");
        byte[] plain=encode(entries), header=new byte[64]; header[0]='T'; header[1]='B'; header[2]=1;
        System.arraycopy(wrapped,0,header,4,60);
        FileOutputStream out=null;
        try {
            byte[] sealed=CryptoBox.seal(key,plain,header);
            if(header.length+sealed.length>CryptoBox.MAX_FILE) throw new IOException("Vault full");
            out=file.startWrite(); out.write(header); out.write(sealed); file.finishWrite(out); out=null;
        } finally { if(out!=null)file.failWrite(out); Arrays.fill(plain,(byte)0); }
    }
    void lock() { if(key!=null)Arrays.fill(key,(byte)0); key=null; wrapped=null; opening=null; }
    static byte[] encode(List<Entry> entries) throws JSONException {
        JSONArray a=new JSONArray();
        for(Entry e:entries)a.put(new JSONObject().put("id",e.id).put("text",e.text).put("created",e.created).put("updated",e.updated));
        return new JSONObject().put("version",1).put("entries",a).toString().getBytes(StandardCharsets.UTF_8);
    }
    static ArrayList<Entry> decode(byte[] plain) throws Exception {
        JSONObject root=new JSONObject(new String(plain,StandardCharsets.UTF_8));
        if(root.getInt("version")!=1)throw new IOException("Unknown version");
        JSONArray a=root.getJSONArray("entries"); if(a.length()>20000)throw new IOException("Too many entries");
        ArrayList<Entry> list=new ArrayList<>(); HashSet<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.getJSONObject(i); String id=o.getString("id"), text=o.getString("text"); long created=o.getLong("created"),updated=o.getLong("updated");
            UUID.fromString(id);
            if(!ids.add(id)||text.length()>200000||text.trim().isEmpty()||created<0||updated<0)throw new IOException("Invalid entry");
            list.add(new Entry(id,text,created,updated));
        }
        return list;
    }
}
