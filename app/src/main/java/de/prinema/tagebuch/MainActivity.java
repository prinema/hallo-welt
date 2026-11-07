package de.prinema.tagebuch;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;

public class MainActivity extends Activity {
    private static final int BG=0xFFFFFCF8, INK=0xFF2A2926, MUTED=0xFF77716A, ACCENT=0xFFC84937, PEACH=0xFFFFE8D9, YELLOW=0xFFFFF1C7;
    private static final int EXPORT=100, IMPORT=101;
    private Vault vault; private ArrayList<Vault.Entry> entries=new ArrayList<>();
    private LinearLayout root, body; private EditText editor; private Vault.Entry editing;
    private boolean unlocked, started, pendingAuth, authenticating, busy;
    private int generation; private Cipher grantedCipher; private CancellationSignal cancellation;
    private byte[] exportBytes, importBytes; private AlertDialog dialog;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Locale locale=Locale.GERMAN;
    @Override public void onCreate(Bundle state) {
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        vault=new Vault(this); lockedScreen();
    }
    @Override protected void onStart() {
        super.onStart(); started=true;
        if(pendingAuth) { pendingAuth=false; completeUnlock(grantedCipher); grantedCipher=null; }
    }
    @Override protected void onStop() {
        if(unlocked && editor!=null && !saveEditor(false)) toast("Entwurf konnte nicht gespeichert werden.");
        started=false; lockSession(); super.onStop();
    }
    @Override protected void onDestroy() { if(cancellation!=null)cancellation.cancel(); worker.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { /* No diary plaintext in saved instance state. */ }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void base() {
        editor=null; editing=null;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets x=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(22)+x.left,x.top+dp(12),dp(22)+x.right,x.bottom+dp(12));return insets;});
        setContentView(root); root.requestApplyInsets();
    }
    private Button button(String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:ACCENT);
        b.setMinHeight(dp(52));b.setPadding(dp(14),dp(4),dp(14),dp(4));b.setElevation(0);b.setBackground(rounded(primary?ACCENT:PEACH,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(8);b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;
    }
    private void header(String title,Runnable back,boolean lock){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        if(back!=null){TextView b=text("‹",38,INK);b.setGravity(Gravity.CENTER);b.setContentDescription("Zurück");bar.addView(b,new LinearLayout.LayoutParams(dp(42),dp(52)));b.setOnClickListener(v->back.run());}
        TextView h=text(title,28,INK);h.setTypeface(null,1);bar.addView(h,new LinearLayout.LayoutParams(0,dp(56),1));h.setGravity(Gravity.CENTER_VERTICAL);
        if(lock){TextView b=text("Sperren",14,ACCENT);b.setPadding(dp(8),dp(16),0,dp(16));bar.addView(b);b.setOnClickListener(v->{saveEditor(false);lockSession();});}
        root.addView(bar);space(root,12);
    }
    private LinearLayout scroll(){ScrollView s=new ScrollView(this);s.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);s.addView(body);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));return body;}
    private void lockedScreen(){
        base();LinearLayout center=new LinearLayout(this);center.setOrientation(1);center.setGravity(Gravity.CENTER);root.addView(center,new LinearLayout.LayoutParams(-1,0,1));
        TextView brand=text("Tagebuch",19,MUTED);brand.setGravity(Gravity.CENTER);center.addView(brand);space(center,36);
        View sun=new View(this){final Paint p=new Paint(3);@Override protected void onDraw(Canvas c){super.onDraw(c);p.setColor(YELLOW);c.drawCircle(getWidth()/2f,getHeight()/2f,dp(90),p);p.setColor(0xFFF6B56B);p.setStrokeWidth(dp(5));p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(getWidth()/2f+dp(65),dp(15),getWidth()/2f+dp(72),dp(1),p);}};
        center.addView(sun,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView title=text("Dein Raum.\nDeine Gedanken.",30,INK);title.setGravity(Gravity.CENTER);title.setTypeface(null,1);center.addView(title);space(center,20);
        TextView sub=text("Ein kleiner Moment nur für dich.",16,MUTED);sub.setGravity(Gravity.CENTER);center.addView(sub);
        root.addView(button("Entsperren",true,this::authenticate));space(root,10);
        TextView hint=text("Fingerabdruck oder Handy-PIN",13,MUTED);hint.setGravity(Gravity.CENTER);root.addView(hint);space(root,16);
    }
    private void authenticate(){
        if(authenticating)return;
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(!km.isDeviceSecure()){showInfo("Displaysperre einrichten","Richte zuerst eine PIN, ein Muster oder ein Passwort in den Android-Einstellungen ein.");return;}
        try {
            Cipher cipher=vault.prepare(); cancellation=new CancellationSignal(); authenticating=true;
            BiometricPrompt prompt=new BiometricPrompt.Builder(this).setTitle("Tagebuch entsperren")
                .setSubtitle("Deine Gedanken bleiben bei dir")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
            prompt.authenticate(new BiometricPrompt.CryptoObject(cipher),cancellation,getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){authenticating=false;Cipher c=result.getCryptoObject().getCipher();if(started)completeUnlock(c);else{pendingAuth=true;grantedCipher=c;}}
                @Override public void onAuthenticationError(int code,CharSequence message){authenticating=false;vault.lock();if(started)toast("Entsperren abgebrochen. Bitte erneut versuchen.");}
            });
        }catch(Exception e){authenticating=false;vault.lock();showInfo("Entsperren nicht möglich","Der Geräteschlüssel ist nicht verfügbar oder die Datei ist beschädigt. Vorhandene Daten bleiben unverändert. Nach einem Zurücksetzen der App kannst du eine Passwort-Sicherung importieren.");}
    }
    private void completeUnlock(Cipher c){
        try{entries=vault.finish(c);unlocked=true;generation++;showEntries();if(importBytes!=null)passwordDialog(false);}
        catch(Exception e){lockSession();showInfo("Tagebuch konnte nicht geöffnet werden","Bitte erneut entsperren. Falls die Gerätesperre entfernt oder die Daten beschädigt wurden, ist eine Passwort-Sicherung zur Wiederherstellung nötig.");}
    }
    private void lockSession(){
        generation++;unlocked=false;busy=false;
        if(dialog!=null){dialog.dismiss();dialog=null;}
        if(editor!=null)editor.setText("");
        for(Vault.Entry e:entries)e.text="";entries.clear();if(!authenticating) vault.lock();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
        lockedScreen();
    }
    private String date(long time,String pattern){return new SimpleDateFormat(pattern,locale).format(new Date(time));}
    private void showEntries(){
        if(!unlocked)return;base();header("Tagebuch",null,true);
        LinearLayout tools=new LinearLayout(this);TextView subtitle=text("Platz für alles, was dich bewegt.",14,MUTED);tools.addView(subtitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=text("Sicherungen",14,ACCENT);settings.setPadding(dp(4),dp(4),0,dp(12));settings.setOnClickListener(v->showBackups());tools.addView(settings);root.addView(tools);
        LinearLayout list=scroll();
        ArrayList<Vault.Entry> sorted=new ArrayList<>(entries);sorted.sort((a,b)->Long.compare(b.created,a.created));String previous="";
        for(Vault.Entry e:sorted){
            String group=date(e.created,"EEEE, d. MMMM yyyy");
            if(!group.equals(previous)){space(list,18);TextView day=text(group,15,INK);day.setTypeface(null,1);day.setPadding(dp(12),dp(10),dp(12),dp(10));day.setBackground(rounded(previous.isEmpty()?PEACH:YELLOW,12));list.addView(day);previous=group;}
            space(list,10);LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(15),dp(16),dp(17));card.setBackground(rounded(Color.WHITE,18));
            card.addView(text(date(e.created,"HH:mm"),13,ACCENT));space(card,8);TextView snippet=text(e.text,17,INK);snippet.setMaxLines(4);snippet.setEllipsize(android.text.TextUtils.TruncateAt.END);snippet.setLineSpacing(dp(3),1);card.addView(snippet);list.addView(card);card.setOnClickListener(v->showEditor(e));
        }
        if(entries.isEmpty()){space(list,56);TextView empty=text("Deine erste Seite wartet.",24,INK);empty.setTypeface(null,1);list.addView(empty);space(list,12);list.addView(text("Ein Gedanke, ein schöner Moment oder einfach dein Tag. Schreib, wie es sich für dich richtig anfühlt.",17,MUTED));}
        space(list,24);root.addView(button("＋  Neuer Eintrag",true,()->showEditor(null)));
    }
    private void showEditor(Vault.Entry e){
        base();editing=e;header(e==null?"Dein Moment":date(e.created,"d. MMM · HH:mm"),()->{if(saveEditor(false))showEntries();},false);
        LinearLayout actions=new LinearLayout(this);Button save=button("Speichern",true,()->{if(saveEditor(true)){toast("Gespeichert");showEntries();}});actions.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        if(e!=null){Button del=button("Löschen",false,()->confirmDelete(e));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(105),dp(50));p.leftMargin=dp(10);actions.addView(del,p);}root.addView(actions);space(root,18);
        editor=new EditText(this);editor.setGravity(Gravity.TOP);editor.setTextSize(19);editor.setTextColor(INK);editor.setHintTextColor(MUTED);editor.setHint("Was möchtest du festhalten?");editor.setBackgroundColor(Color.TRANSPARENT);editor.setPadding(0,dp(10),0,0);editor.setLineSpacing(dp(5),1);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);editor.setSaveEnabled(false);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200000)});
        if(e!=null)editor.setText(e.text);root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));space(root,8);root.addView(text("Wird beim Verlassen verschlüsselt gespeichert.",12,MUTED));
    }
    private boolean saveEditor(boolean explicit){
        if(editor==null||!unlocked)return true;
        String value=editor.getText().toString();
        if(value.trim().isEmpty()){if(explicit)toast("Schreib zuerst ein paar Worte.");return !explicit;}
        if(editing!=null&&value.equals(editing.text))return true;
        Vault.Entry next=new Vault.Entry(editing==null?UUID.randomUUID().toString():editing.id,value,editing==null?System.currentTimeMillis():editing.created,System.currentTimeMillis());
        ArrayList<Vault.Entry> changed=new ArrayList<>(entries);if(editing!=null)changed.remove(editing);changed.add(next);
        try{vault.save(changed);entries=changed;editing=next;return true;}catch(Exception ex){toast("Speichern fehlgeschlagen. Bitte Text kürzen oder Speicherplatz prüfen.");return false;}
    }
    private void confirmDelete(Vault.Entry e){
        dialog=new AlertDialog.Builder(this).setTitle("Eintrag löschen?").setMessage("Dieser Eintrag wird aus deinem Tagebuch entfernt.")
            .setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{ArrayList<Vault.Entry> next=new ArrayList<>(entries);next.remove(e);try{vault.save(next);entries=next;editor=null;editing=null;showEntries();}catch(Exception ex){toast("Löschen fehlgeschlagen.");}}).show();secureDialog();
    }
    private void showBackups(){
        base();header("Sicherungen",this::showEntries,false);LinearLayout l=scroll();space(l,16);l.addView(text("Deine Einträge bleiben auf diesem Gerät.",17,INK));space(l,24);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(18),dp(20),dp(18),dp(20));card.setBackground(rounded(YELLOW,20));
        TextView h=text("Deine Gedanken mitnehmen",21,INK);h.setTypeface(null,1);card.addView(h);space(card,12);card.addView(text("Eine verschlüsselte Datei schützt deine Einträge mit einem eigenen Passwort. Damit kannst du sie auch auf einem neuen Handy wiederherstellen.",16,INK));l.addView(card);space(l,24);
        l.addView(button("Sicherung exportieren",true,()->passwordDialog(true)));l.addView(button("Sicherung importieren",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}));space(l,22);
        l.addView(text("Beim Import werden Einträge ergänzt. Identische Einträge werden übersprungen; geänderte Fassungen bleiben als eigene Einträge erhalten.",14,MUTED));space(l,18);l.addView(text("Bewahre das Sicherungspasswort auf. Es wird nicht gespeichert und kann nicht zurückgesetzt werden.",14,MUTED));space(l,18);l.addView(text("Schutz: AES-256-GCM · Android Keystore\nSicherungen: Argon2id · 64 MiB · 3 Durchläufe\nKeine Internetberechtigung · Android 11 oder neuer",12,MUTED));
    }
    private void passwordDialog(boolean exporting){
        if(!unlocked||busy)return;
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(24),dp(12),dp(24),dp(4));
        l.addView(text(exporting?"Wähle ein eigenes Passwort mit mindestens 12 Zeichen.":"Gib das Passwort dieser Sicherung ein.",15,INK));
        EditText pw=new EditText(this);pw.setHint("Sicherungspasswort");pw.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pw.setSaveEnabled(false);pw.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);pw.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);l.addView(pw);
        EditText repeat=new EditText(this);if(exporting){repeat.setHint("Passwort wiederholen");repeat.setInputType(pw.getInputType());repeat.setSaveEnabled(false);repeat.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);repeat.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);l.addView(repeat);}
        dialog=new AlertDialog.Builder(this).setTitle(exporting?"Sicherung erstellen":"Sicherung öffnen").setView(l).setNegativeButton("Abbrechen",(d,w)->{if(!exporting)importBytes=null;}).setPositiveButton(exporting?"Weiter":"Importieren",null).create();
        dialog.setOnDismissListener(d->{pw.setText("");repeat.setText("");});dialog.show();secureDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pass=pw.getText().toString();if(exporting&&(pass.length()<12||!pass.equals(repeat.getText().toString()))){pw.setError("Mindestens 12 Zeichen; beide Eingaben müssen übereinstimmen.");return;}
            if(pass.isEmpty()){pw.setError("Bitte Passwort eingeben.");return;}
            char[] secret=pass.toCharArray();dialog.dismiss();if(exporting)exportBackup(secret);else importBackup(secret);
        });
    }
    private void exportBackup(char[] password){
        final byte[] plain;try{plain=Vault.encode(entries);}catch(Exception ex){Arrays.fill(password,'\0');toast("Export fehlgeschlagen.");return;}
        final int token=generation;busy=true;toast("Sicherung wird verschlüsselt …");
        worker.execute(()->{byte[] output=null;try{output=CryptoBox.backup(plain,password);}catch(Exception ignored){}finally{Arrays.fill(plain,(byte)0);Arrays.fill(password,'\0');}
            final byte[] result=output;runOnUiThread(()->{if(token!=generation||!unlocked)return;busy=false;if(result==null){toast("Export fehlgeschlagen.");return;}exportBytes=result;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"Tagebuch-"+date(System.currentTimeMillis(),"yyyy-MM-dd-HHmm")+".tagebuch");startActivityForResult(i,EXPORT);});});
    }
    private void importBackup(char[] password){
        final byte[] source=importBytes;importBytes=null;if(source==null){Arrays.fill(password,'\0');return;}
        final int token=generation;busy=true;toast("Sicherung wird geöffnet …");
        worker.execute(()->{ArrayList<Vault.Entry> restored=null;byte[] plain=null;try{plain=CryptoBox.restore(source,password);restored=Vault.decode(plain);}catch(Exception ignored){}finally{if(plain!=null)Arrays.fill(plain,(byte)0);Arrays.fill(password,'\0');}
            final ArrayList<Vault.Entry> result=restored;runOnUiThread(()->{if(token!=generation||!unlocked){if(result!=null)for(Vault.Entry e:result)e.text="";return;}busy=false;if(result==null){toast("Passwort falsch oder Sicherung beschädigt.");return;}
                ArrayList<Vault.Entry> next=new ArrayList<>(entries);HashMap<String,Vault.Entry> known=new HashMap<>();for(Vault.Entry e:next)known.put(e.id,e);int added=0;
                for(Vault.Entry e:result){Vault.Entry old=known.get(e.id);if(old!=null){if(old.text.equals(e.text))continue;e.id=UUID.randomUUID().toString();}next.add(e);known.put(e.id,e);added++;}
                try{vault.save(next);entries=next;showEntries();toast(added+" Einträge importiert.");}catch(Exception ex){toast("Import konnte nicht gespeichert werden. Deine bisherigen Einträge bleiben erhalten.");}
            });});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT){byte[] bytes=exportBytes;exportBytes=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException();out.write(bytes);toast("Verschlüsselte Sicherung gespeichert.");}catch(Exception e){toast("Sicherung konnte nicht geschrieben werden.");}}}
        if(request==IMPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(InputStream in=getContentResolver().openInputStream(data.getData())){importBytes=CryptoBox.readLimited(in);if(unlocked)passwordDialog(false);else toast("Entsperre das Tagebuch für den Import.");}catch(Exception e){toast("Datei nicht lesbar oder größer als 8 MB.");}}
    }
    private void secureDialog(){if(dialog!=null&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void showInfo(String title,String message){dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Verstanden",null).show();secureDialog();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(unlocked&&editor!=null){if(saveEditor(false))showEntries();}else if(unlocked)showEntries();else super.onBackPressed();}
}
