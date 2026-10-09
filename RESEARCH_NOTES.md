# Technical research notes

## Offline translation

- Google ML Kit Android guide: https://developers.google.com/ml-kit/language/translation/android
  - On-device translation uses dynamic model downloads and `Translator`.
  - Models are around 30 MB each; Wi-Fi-only download is recommended.
- ML Kit language support: https://developers.google.com/ml-kit/language/translation/translation-language-support
  - English (`en`) and Persian (`fa`) are supported.

## Embedded English TTS

- Sherpa Piper guide: https://k2-fsa.github.io/sherpa/onnx/tts/piper.html
  - Piper voices can be converted for Sherpa using ONNX metadata and `tokens.txt`.
  - The shared eSpeak data archive is used for phonemization.
- Piper project page: https://github.com/rhasspy/piper
  - The original repository is archived and points to the moved project.
- Piper voice catalog/model source: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/amy/medium
  - Amy is the selected English female voice.
- An int8 quantized Amy Low build was tested on a real phone but produced unacceptable articulation, so the app now packages the unquantized Amy Medium model for clearer speech. The voice archive is larger, but quality is prioritized over the failed aggressive quantization.
- Piper voice samples: https://rhasspy.github.io/piper-samples/
  - Piper quality tiers include x_low, low, medium, and high; low is selected as the size/quality compromise.
