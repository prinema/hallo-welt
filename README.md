# Tagebuch für Android

Helles, lokales Tagebuch mit Apricot-, Sonnengelb- und Korallakzenten. Android 11 (API 30) oder neuer.

## Funktionen
- Mehrere reine Texteinträge pro Tag, automatisch mit Datum und Uhrzeit.
- Schreiben, Bearbeiten, Löschen. Entwurf wird beim Verlassen gespeichert.
- Android-Systemdialog: starker Fingerabdruck oder Gerätesperre (PIN/Muster/Passwort).
- Sofortiges Sperren im Hintergrund, geschützte Bildschirmvorschau und deaktivierte Screenshots.
- Verschlüsselte Sicherungsdatei per Android-Dateiauswahl exportieren/importieren.
- Import ergänzt Einträge, überspringt identische UUID/Text-Paare und behält geänderte Fassungen separat.
- Keine Internetberechtigung, Analyse, Konten oder Werbung.

## Verschlüsselung
AES-256-GCM für die gesamte lokale JSON-Nutzlast einschließlich Zeitangaben, mit frischer 96-Bit-Nonce pro Schreibvorgang. Ein zufälliger 256-Bit-Datenschlüssel wird mit einem nicht exportierbaren, authentifizierungsgebundenen Android-Keystore-Schlüssel umhüllt. Der Systemdialog autorisiert die konkrete Cipher-Operation (auth-per-use); kein selbstgebauter PIN-Dialog und kein zeitbasiertes Authentifizierungsfenster. Nutzlast und Formatheader sind authentifiziert. Atomare Dateischreibvorgänge über AtomicFile.

Export: Argon2id v1.3, 64 MiB, 3 Iterationen, Parallelität 1, 128-Bit-Zufallssalt; daraus ein AES-256-GCM-Schlüssel. Fest versioniertes Dateiformat, kein durch Datei-Parameter steuerbarer Speicherverbrauch. Keine Passwörter im Dateiformat. Mindestlänge beim Export 12 Zeichen. Sicherungsdateien enthalten eigenständig wiederherstellbare Einträge, keinen gerätegebundenen Keystore-Schlüssel.

Klartexte werden ausschließlich im entsperrten Prozess verwendet. Java-Strings und Tastaturen erlauben keine garantierte vollständige Speicherlöschung. Schlüssel-Bytearrays und Passwort-Chararrays werden nach Gebrauch überschrieben. App-Daten werden aus automatischer Cloud-Sicherung und Geräteübertragung ausgeschlossen. Passwort-Sicherungen sind der vorgesehene Wiederherstellungsweg. Entfernen/Zurücksetzen der Gerätesperre kann den Keystore-Schlüssel unbrauchbar machen. App-Daten nicht löschen, ohne eine aktuelle Sicherung zu besitzen.

## Bauen und Prüfen
JDK 17, Gradle 8.9, Android SDK 35. Kein Gradle Wrapper enthalten; CI richtet Gradle ein.

    gradle testDebugUnitTest lintDebug assembleDebug

CI lädt die Debug-APK und Testberichte hoch. Tests decken Rundläufe, zufällige Nonces, falsche Schlüssel/Passwörter, manipulierte Ciphertexte/Header und Dateigrößenlimits ab. Fingerabdruck, Gerätesperre und Hintergrundwechsel müssen zusätzlich auf dem konkreten Android-Handy ausprobiert werden; kein unabhängiges Sicherheitsaudit.

Version 1.1 führt einen dauerhaft aufbewahrten privaten Signierschlüssel ein. Der Wechsel von der ersten Testversion erfordert einmalig Export, Neuinstallation und Import. Ab 1.1 immer denselben privaten Schlüssel verwenden und versionCode erhöhen, damit Android Updates ohne Löschen der Einträge annimmt.

Das private Archiv `Tagebuch-Signierschluessel.zip` wird separat aufbewahrt und gehört niemals in dieses öffentliche Repository. Es enthält PKCS12-Keystore und Passwortdatei. Das CI-Artefakt ist ein Zwischenprodukt; vor Auslieferung mit dem privaten Schlüssel signieren:

    python3 scripts/sign_apk.py --input app-debug.apk --signer apksigner.jar --keystore /private/tagebuch-release.p12 --password-file /private/signing-password.txt --output Tagebuch-1.1.apk

Die Signaturprüfung ist Teil dieses Skripts. Die übergebene Passwortdatei wird nicht in die APK eingebaut. Der Signierschlüssel ist unabhängig von der Verschlüsselung der Tagebucheinträge.

## Grenzen der ersten Version
Maximal 8 MiB pro Datei und 200.000 Zeichen pro Eintrag. Die gesamte verschlüsselte Datei wird beim Speichern atomar ersetzt. Große Tagebücher sind daher für eine spätere Datenbankversion vorgesehen. Wer die Geräte-PIN kennt, kann die App entsperren. Eine bereits entsperrte, kompromittierte Laufzeit ist nicht durch Dateiverschlüsselung abgedeckt.
