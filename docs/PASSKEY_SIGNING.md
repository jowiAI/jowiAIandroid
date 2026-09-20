# Passkeys — what Django needs from the Android side

Android is the only platform where the WebAuthn origin is **not** a URL. Credential
Manager reports

```
android:apk-key-hash:<base64url of the SHA-256 of the signing certificate, no padding>
```

so every key that ever signs the app needs its fingerprint on the server. Until they are
there, `https://workis.ai/.well-known/assetlinks.json` answers **404** and every assertion
from Android is refused with `code: "verify"`. Nothing else in the passkey flow can be
tested before this lands — see `jowiAIs/docs/API_CONTRACT.md` §2 "Passkeys".

`rpIdHash` is `workis.ai` for all of them; only the origin differs per key.

## The two keys this machine holds

| key | where it applies |
|---|---|
| **debug** (`~/.android/debug.keystore`, alias `androiddebugkey`) | every `assembleDebug` install, including the emulator |
| **upload** (`keystore/jowi-release.jks`, alias `jowi`) | what we sign a release with before Play re-signs it |

```
debug
  sha256_cert_fingerprints : ED:FC:25:CE:80:AF:F4:6F:3D:AC:80:6E:5F:4B:D9:AD:43:84:99:5C:72:F8:05:8C:84:02:BF:BD:15:8B:EB:7A
  origin                   : android:apk-key-hash:7fwlzoCv9G89rIBuX0vZrUOEmVxy-AWMhAK_vRWL63o

upload
  sha256_cert_fingerprints : 29:51:47:20:89:EA:EC:51:9A:DA:9E:21:34:22:5A:4C:91:46:18:C9:4C:D2:61:2C:AB:BB:74:01:88:A4:73:07
  origin                   : android:apk-key-hash:KVFHIInq7FGa2p4hNCJaTJFGGMlM0mEsq7t0AYikcwc
```

## The third key — only Play has it

With Play App Signing the store re-signs the upload with its own key, so an app installed
from Play reports **neither** of the two above. Its fingerprint comes from
**Play Console → the app → Test and release → Setup → App signing → "App signing key
certificate" → SHA-256**. That one matters for every real install; the upload key matters
only for a build sideloaded before Play re-signs it.

Turn any SHA-256 into the origin form with:

```bash
python3 -c 'import base64,sys; print("android:apk-key-hash:"+base64.urlsafe_b64encode(bytes.fromhex(sys.argv[1].replace(":",""))).decode().rstrip("="))' <SHA-256>
```

## What to hand over

- `WORKIS_PASSKEY_ANDROID_FINGERPRINTS` — the SHA-256 values (colon-separated hex), for
  `assetlinks.json` `sha256_cert_fingerprints`.
- `WORKIS_PASSKEY_ORIGINS` — the matching `android:apk-key-hash:…` values, for the verifier.

Both lists want the Play app-signing key **and** the debug key; the upload key is worth
adding while internal builds are sideloaded.

## Regenerating these

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
"$JAVA_HOME/bin/keytool" -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android | grep SHA256:
```

The debug keystore is per machine: another developer's builds report a different origin and
need their own fingerprint added.
