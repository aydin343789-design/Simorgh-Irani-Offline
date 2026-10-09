# Offline TTS selection and packaging

## Selected model

- Language: US English only; no Persian TTS model is shipped, per product decision.
- Voice: Piper Amy, one speaker, medium quality, 22.05 kHz. Official Sherpa-ONNX prequantized INT8 archive: [`vits-piper-en_US-amy-medium-int8.tar.bz2`](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium-int8.tar.bz2).
- Upstream model card identifies Amy and refers to [Mycroft Mimic3 Voices](https://github.com/MycroftAI/mimic3-voices) for source/training data. The voice repository license is CC BY-SA 4.0.
- Source archive: 21,028,122 bytes; SHA-256 `bd23c0aa629eb3719448582f45ede49e8fa6a679061fed5eab16a6a6fd8e7e82`.
- ONNX voice model: 18,681,741 bytes; SHA-256 `e93cf3361c5561b9fedcec20b26ae5ecd068172e514740562f1263be65b3848f`.
- Prepared app asset (English model + tokens + minimal English eSpeak data + model card): about 19.2 MB compressed / 19.6 MB expanded. `scripts/prepare-tts-models.py` rebuilds this asset at APK-build time and checks the archive/model hashes. Runtime TTS makes no network requests.

## Android runtime

- Sherpa-ONNX Java wrappers and JNI libraries are already vendored under `android/app/src/main/java/com/k2fsa/sherpa/onnx/` and `android/app/src/main/jniLibs/arm64-v8a/`.
- The app uses `OfflineTts` VITS config, two CPU threads, and `AudioTrack` to play generated PCM. It does not call Android `TextToSpeech` or Web Speech API.
- Release/debug build uses R8; ProGuard keeps Sherpa JNI wrappers and native methods.
- Model archive extraction is private app storage, checksum-validated, bounded to 25 MiB expanded, and guarded against path traversal.

## Source and integration checks

- Sherpa Android docs: https://k2-fsa.github.io/sherpa/onnx/android/index.html
- Sherpa Piper TTS docs: https://k2-fsa.github.io/sherpa/onnx/tts/piper.html
- Sherpa model release: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- Amy voice source/CC BY-SA license: https://github.com/MycroftAI/mimic3-voices/blob/master/LICENSE
- Sherpa runtime license: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE (Apache-2.0)
