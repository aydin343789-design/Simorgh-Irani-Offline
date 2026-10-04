# Offline Language Features — Acceptance Tests

## Preconditions

1. Install the APK on an Android arm64 device with current Google Play services.
2. Confirm Wi‑Fi is available for the one-time ML Kit translation-model download.
3. No Android TTS voice or browser voice setting is required for the English speech tests.

## Translator — first-time setup

1. Open **مترجم فارسی ↔ انگلیسی**.
2. Verify the initial status states that translation models are not installed.
3. Tap **دریافت مدل‌های آفلاین**.
4. Confirm the dialog that states the approximate total download size (about 60 MB) and Wi‑Fi-only condition.
5. Confirm the status changes to ready only after both Persian and English models have downloaded.
6. Turn off Wi‑Fi and mobile data, then reopen the translator. Its status must still show that on-device translation is ready.

## Translator — offline operation

With Wi‑Fi and mobile data disabled, verify both directions:

| Input | Direction | Expected behavior |
|---|---|---|
| `Hello, how are you?` | English → Persian | Complete Persian sentence; no English words leaked into the result |
| `I love learning English.` | English → Persian | Complete Persian translation |
| `سلام، حالت چطوره؟` | Persian → English | Complete English sentence |
| `من عاشق یادگیری زبان انگلیسی هستم.` | Persian → English | Complete English translation |
| `thank you` | English → Persian | `ممنون` or a natural Persian equivalent |
| `کرایه ماشین چقدر است؟` | Persian → English | Complete English question |

Also verify that copying, local history, restart persistence and the selected result language work as expected.

## Independent English voice

1. Install the APK on a clean device with Android TTS disabled or with no TTS voice installed.
2. Open an English flashcard and tap the speaker. It must produce the bundled **Piper Amy** female English voice.
3. Verify that the first run extracts the internal asset and that later runs remain offline.
4. Disable network access and repeat speech. Playback must still work.
5. Tap speech twice quickly. The previous `AudioTrack` must stop before the next clip begins.
6. Open a Persian flashcard or Persian translation result. The app must display that the embedded voice currently supports English only; it must not silently fall back to Android TTS or browser speech.
7. If a user-uploaded audio file exists for a word, it may play before the embedded voice.

## Behavior before translation models are installed

1. Clear the app’s translation models through Android app data reset, then open the translator without network access.
2. Test a known phrase such as `hello`; the local phrasebook may answer it.
3. Test a sentence outside the phrasebook such as `I love learning English.`
4. The UI must ask the user to download models; it **must not** display a mixed result such as `I Love دارم` as a completed translation.

## Build verification

1. Run `npm ci`, `npm run verify:translator` and `npm run sync`.
2. Confirm `dist/index.html` and `android/app/src/main/assets/public/index.html` are generated from `www/index.html`.
3. Build with `cd android && ./gradlew assembleDebug`.
4. Confirm R8 is enabled for debug and release and only `arm64-v8a` is packaged.
5. Run `unzip -l app-debug.apk` and verify the APK contains `assets/tts/tts-en.zip` and does not contain `tts-fa.zip`, `speechSynthesis`, or Android TTS code.
6. Verify the APK contains the Sherpa/ONNX native libraries and the internal voice asset; no network fetch is needed for speech.
