# Offline Language Features — Acceptance Tests

## Translator (all checks can run without Internet)

1. Install the APK, then disable Wi‑Fi and mobile data before opening it.
2. Open **مترجم آفلاین فارسی ↔ انگلیسی**.
3. Test known phrases in both directions:
   - `hello` → `سلام`
   - `Hello, how are you?` → `سلام، حالت چطوره؟`
   - `سلام حالت چطوره` → `hello, how are you?`
   - `thank you` → `متشکرم`
   - `ممنون` → `thank you`
4. Test a vocabulary word already included in the lessons, such as `apple` ↔ `سیب`.
5. Test an unknown sentence. The app should say it is not in the offline dictionary and must not send it to a network service.
6. Restart the app while still offline; known translations and local translation history should remain available.

## Speech playback

1. In Android Text-to-speech settings, confirm whether the device has installed offline English and Persian voices.
2. Open an English flashcard and tap the speaker; it should speak the English word.
3. Flip the card and tap its speaker; it should speak the Persian word. Tap **مثال انگلیسی** to hear its example.
4. Open a translation result and tap **خواندن ترجمه**; it should choose Persian or English from the text script.
5. Open conversation practice and test the separate English and Persian speaker buttons.
6. If a local voice for a language is unavailable, the app should explain that and must not fall back to a network voice.
7. Repeat with Internet disabled; playback should work for voices reported as offline by Android.

## Conversation practice

- The greetings, cafe, directions, shopping, and travel scenarios render in both languages.
- Topic buttons change the selected dialogue.
- English and Persian audio buttons use the corresponding local voice independently.

## APK packaging and obfuscation

1. Run `npm ci` and `npm run sync`.
2. Confirm `dist/index.html` and `android/app/src/main/assets/public/index.html` are generated from the source and contain obfuscated inline scripts.
3. Build the debug APK using `cd android && ./gradlew assembleDebug`.
4. Confirm R8 is enabled for debug/release. Obfuscation should make extraction less readable; it is not a guarantee against reverse engineering.
