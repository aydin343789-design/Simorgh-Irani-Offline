# ML Kit translation research (2026-10-08)

- Official Android integration docs: https://developers.google.com/ml-kit/language/translation/android
  - Dependency `com.google.mlkit:translate:17.0.3`.
  - Supports dynamic translation and explicitly managed language models; Google recommends Wi‑Fi for downloads.
  - Documented approximate model size: about 30 MB per language model.
- Official language support/API reference: https://developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateLanguage
  - Persian/Farsi is `fa`; English is `en`.
- Official `TranslateRemoteModel` reference: https://developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateRemoteModel
  - English is built in and must not be instantiated as a downloadable/deletable model; `getLanguage()` identifies downloaded models.
- Official model manager API: https://developers.google.com/android/reference/com/google/mlkit/common/model/RemoteModelManager
  - `download`, `getDownloadedModels`, and `isModelDownloaded` support explicit management. The app checks that Persian is already present before calling `translate` so a translation attempt does not initiate the consent-gated first download.
- Official quality/privacy and service terms: https://developers.google.com/ml-kit/language/translation and https://developers.google.com/ml-kit/terms
  - Translation text is processed on device and is not sent to Google for translation. Quality is intended for casual use and is not guaranteed perfect.
  - ML Kit may occasionally contact Google for metrics, fixes, model updates, and compatibility information. This means app translation works offline after setup, but the app cannot guarantee that the SDK will never attempt any network connection.
- Pinned Google Maven dependency POM: https://dl.google.com/dl/android/maven2/com/google/mlkit/translate/17.0.3/translate-17.0.3.pom
  - Confirms the published version and its Android/Google runtime dependencies.

Implementation decision: download only the Persian model (estimated ~30 MB), only after the app confirmation dialog, with `DownloadConditions.requireWifi()`. English is built in. The Android bridge rejects translation until the Persian model is reported downloaded; the JS output guard refuses results containing both Persian and Latin letters to avoid displaying obvious mixed-script output.
