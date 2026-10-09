# Argos / CTranslate2 Android feasibility

## Provided model

Source URL supplied for testing:

- https://pub-dbae765fb25a4114aac1c88b90e94178.r2.dev/v1/translate-en_fa-1_5.argosmodel
- Matching reverse model URL: https://pub-dbae765fb25a4114aac1c88b90e94178.r2.dev/v1/translate-fa_en-1_5.argosmodel

The English-to-Persian package was downloaded and inspected. It is a ZIP-based `.argosmodel` package with metadata:

- `from_code`: `en`
- `to_code`: `fa`
- package/Argos version: `1.5`
- compressed download size: approximately 119 MB
- `model/model.bin`: approximately 132 MB uncompressed
- also contains SentencePiece vocabulary/model and Stanza sentence-boundary data

The package README credits OPUS, Wiktionary/Wiktextract, Stanza and the listed corpus authors. Preserve those credits when redistributing.

The package was tested with the Python Argos runtime. Example output:

```text
I love learning English.
→ من عاشق یادگیری انگلیسی هستم.
```

## Official references

- Argos package format and runtime API: https://argos-translate.readthedocs.io/en/latest/source/argostranslate.html
- Argos source/runtime: https://github.com/argosopentech/argos-translate
- CTranslate2 documentation: https://opennmt.net/CTranslate2/
- CTranslate2 source: https://github.com/OpenNMT/CTranslate2
- Sherpa/Piper conversion reference used elsewhere in this project: https://k2-fsa.github.io/sherpa/onnx/tts/piper.html

## Android feasibility test

CTranslate2 was cross-compiled for `arm64-v8a` with Android NDK 27.2 and CPU-only settings. The native shared library built successfully after disabling Linux-only pthread affinity on Android. This proves the core runtime can be built for Android, but a complete app integration still needs:

1. C++ JNI wrapper around `ctranslate2::Translator`.
2. Android SentencePiece native build and tokenizer/detokenizer bridge.
3. Download/resume/progress/unzip verification for both Argos packages.
4. One model per direction (`en→fa` and `fa→en`), approximately 238 MB compressed total.
5. ARM64 native library packaging and real-phone smoke testing.

The `.argosmodel` file cannot be passed directly to Android or to the existing ML Kit API. It requires the CTranslate2 native runtime and SentencePiece.
