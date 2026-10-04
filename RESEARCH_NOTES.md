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
- Piper voice catalog/model source: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/amy/low
  - Amy is the selected English female voice.
  - The model is quantized to int8 in this project to reduce the packaged voice asset from roughly 63 MB to roughly 18.6 MB.
- Piper voice samples: https://rhasspy.github.io/piper-samples/
  - Piper quality tiers include x_low, low, medium, and high; low is selected as the size/quality compromise.
