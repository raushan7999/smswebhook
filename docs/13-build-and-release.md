# 13 · Build and release

> **Purpose:** the end-to-end procedure for producing, verifying and publishing a release, and for preparing
> the next version.

## 1. Versioning

- Semantic versioning `MAJOR.MINOR.PATCH`, set once in `app/build.gradle.kts` (`appVersion`).
- `versionCode = MAJOR × 10000 + MINOR × 100 + PATCH` (1.0.0 → 10000, 1.0.1 → 10001, 1.2.0 → 10200).
- A versionCode is never reused. Every uploaded build increments it.

> Note: a different, sideloaded build with the same package name (version 2.0.0, versionCode 3) exists on the
> test phone. It is unrelated to this codebase and is signed with an unknown key. Uninstall it before
> installing the release build on that phone.

## 2. Signing

| Item | Value |
|---|---|
| Keystore | `release_item/1_signing_key/smswebhook-release.jks` (PKCS12, RSA 4096, valid until 2056) |
| Alias | `smswebhook` |
| Passwords | `release_item/1_signing_key/keystore.properties` |
| Upload certificate SHA-1 | `03:4B:7F:65:B3:56:4D:2D:FD:60:2B:7A:97:64:6C:54:9D:18:66:3A` |
| Upload certificate SHA-256 | `92:23:11:05:66:8E:94:FE:58:28:A8:9F:48:53:16:57:EA:0F:97:0E:1B:1E:83:C8:8E:DD:EF:30:69:38:45:F6` |

- The build reads `keystore.properties` from the path in `local.properties` (`smswebhook.keystore.properties`),
  defaulting to `release_item/1_signing_key/keystore.properties`. Without it, an unsigned release is built.
- `release_item/1_signing_key/` is gitignored. **Back it up to two separate, secure locations.** With Play App
  Signing this is the *upload* key: if it is lost, Google can reset it, but that takes time.
- Never email the keystore or paste its passwords into chat, tickets or source code.

## 3. Build procedure

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:clean :app:testDebugUnitTest :app:lintRelease :app:bundleRelease :app:assembleRelease
```

| Output | Path |
|---|---|
| App bundle (upload to Play) | `app/build/outputs/bundle/release/app-release.aab` |
| APK (device testing) | `app/build/outputs/apk/release/app-release.apk` |
| R8 mapping | `app/build/outputs/mapping/release/mapping.txt` |

## 4. Verification

```bash
BT=~/Library/Android/sdk/build-tools/36.0.0
$BT/apksigner verify --print-certs app-release.apk        # signer = upload certificate above
$BT/aapt2 dump badging app-release.apk                     # package, versionCode, permissions
$BT/zipalign -c -P 16 4 app-release.apk                    # 16 KB page alignment
shasum -a 256 *.aab *.apk
```

Then run the manual checklist in [12 §4](12-testing.md#4-manual-release-checklist) on a phone.

## 5. Release kit (`release_item/`)

```
release_item/
├── PLAY_STORE_DEPLOYMENT.md        step-by-step checklist for publishing
├── about_app.txt                   identifiers, certificates, URLs
├── 1_signing_key/                  SECRET — upload keystore + passwords (gitignored)
├── 2_release_v1.0.0/               one folder per version
│   ├── sms-webhook-v1.0.0-release.aab
│   ├── sms-webhook-v1.0.0-release.apk
│   ├── mapping.txt
│   ├── build_info.txt              checksums, certificate, checks performed
│   └── release_notes.txt           en-IN and hi-IN (≤ 500 characters each)
├── 3_store_listing/                name, short and full description (en-IN, hi-IN) with character counts
├── 4_graphics/                     icon 512, feature graphic, raw and captioned screenshots, icon sources
├── 5_play_console_forms/           answers for every App content form
├── 6_promo_video_v1.0.0/           promo and teaser videos, soundtrack, thumbnail
├── 7_privacy_policy/               reference copy of the published policy
└── tools/                          scripts that regenerate graphics, videos and the website
```

> **Status (2026-10-10):** the release kit is complete. Remaining steps are manual and listed in
> `release_item/PLAY_STORE_DEPLOYMENT.md` (back up the key, publish the website, real-SMS test, Play Console).

| Tool | Generates |
|---|---|
| `tools/brand.py` | Shared colours (brand green `#16A34A`), fonts (Poppins, OFL), phone mock-up, captions |
| `tools/make_store_images.py <ver>` | Captioned phone, 7-inch and 10-inch screenshots |
| `tools/make_feature_graphic.py <ver>` | 1024×500 feature graphic |
| `tools/make_teaser.py <ver> vertical\|landscape` | Motion-graphics videos |
| `tools/make_audio.py <ver>` | Synthesised soundtrack, muxed into the videos |
| `tools/make_youtube_thumbnail.py <ver>` | YouTube thumbnail and vertical poster |
| `tools/make_website.py <ver>` | The static website from shared templates and the screenshots |

Raw screenshots come from the debug build with demo data ([03 §7](03-developer-setup.md#7-demo-data-debug-builds-only)).
Crop values in `brand.py` match a 1080×2340 screen with a 100 px status bar and a 135 px navigation bar.

## 6. Store assets

| Asset | Requirement | File |
|---|---|---|
| App icon | 512×512, 32-bit PNG | `4_graphics/app-icon-512.png` |
| Feature graphic | 1024×500, 24-bit PNG | `4_graphics/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, 9:16 | `4_graphics/store/phone_v1.0.0/` |
| 7-inch / 10-inch tablet | optional | `4_graphics/store/tablet-*_v1.0.0/` |
| Promo video | YouTube URL, public or unlisted | `6_promo_video_v1.0.0/promo-landscape-1920x1080.mp4` |

## 7. Google Play submission

1. **Create the app** and complete every App content form using `5_play_console_forms/PLAY_CONSOLE_FORMS.md`.
2. **SMS permission declaration** (Policy → App content → Sensitive app permissions). `RECEIVE_SMS` is a
   restricted permission: Google approves it only for default SMS apps or listed exceptions, and requires a
   description and a demo video. **Approval is not guaranteed.** If it is refused, distribute the APK directly
   (website download, enterprise/MDM) or plan a default-SMS-handler variant.
3. **Internal testing:** upload the `.aab`, accept Play App Signing, release name `1.0.0 (10000)`, release notes.
4. **Closed testing:** new personal accounts need ≥ 12 testers for 14 continuous days before production.
5. **Production:** staged rollout (for example 20 % → 100 %); monitor Android vitals.

## 8. Next version routine

1. Bump `appVersion`; implement and test; update [CHANGELOG](CHANGELOG.md) and the affected documents.
2. Build and verify (§3–4); create `release_item/2_release_v<ver>/` with artifacts, `build_info.txt` and
   `release_notes.txt`. Leave earlier version folders untouched.
3. If screens changed: new raw screenshots → `screenshots_v<ver>/`, then regenerate store images, feature
   graphic and videos with the tools.
4. If a permission, SDK or data flow changed: update the privacy policy, Play forms and [11](11-security-and-privacy.md)
   **before** uploading.
5. Upload, roll out, and archive the mapping file.
