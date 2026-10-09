# Third-party notices

## Sherpa-ONNX

The embedded native inference runtime and Java bindings are from Sherpa-ONNX by the k2-fsa project. Source and license information:

- Source: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0

The corresponding source files and native libraries are kept under `android/app/src/main/java/com/k2fsa/sherpa/onnx/` and `android/app/src/main/jniLibs/arm64-v8a/`.

## Piper Amy Low voice

The English female voice is based on the Piper voice published at:

- Model source: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/amy/low
- Samples and voice catalog: https://rhasspy.github.io/piper-samples/

The model is converted for Sherpa-ONNX, quantized to int8, and packaged as `android/app/src/main/assets/tts/tts-en.zip`. Preserve the upstream model metadata and license when redistributing the APK.

## eSpeak phonemizer data

The Piper/Sherpa voice uses the shared eSpeak NG data archive for English phonemization:

- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- Upstream eSpeak NG project: https://github.com/espeak-ng/espeak-ng

The eSpeak data is bundled inside the voice archive and is used only by the embedded native speech path.
