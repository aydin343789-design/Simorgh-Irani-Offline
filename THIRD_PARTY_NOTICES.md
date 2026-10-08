# اعلان اجزای ثالث

این سند فقط دربارهٔ اجزای شخص ثالث است و برای کل برنامه مجوزی تعیین نمی‌کند.

## صدای انگلیسی Amy و مدل Piper

- مدل: صدای تک‌گویندهٔ آمریکایی انگلیسی **Amy**, کیفیت medium، نرخ 22.05 kHz؛ مدل ONNX به‌صورت INT8 quantize و در بستهٔ TTS برنامه قرار می‌گیرد.
- مدل و کارت مدل از انتشار رسمی Sherpa-ONNX با نام `vits-piper-en_US-amy-medium-int8` گرفته می‌شوند. کارت مدل به مجموعهٔ صداهای [Mycroft Mimic3 Voices](https://github.com/MycroftAI/mimic3-voices) ارجاع می‌دهد.
- مخزن صداهای Mycroft تحت [Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/) است. فایل ONNX در آرشیو رسمی Sherpa-ONNX از پیش به INT8 quantize شده و در برنامه بدون تغییر مدل بسته‌بندی می‌شود. متن کامل مجوز در [`licenses/MIMIC3-VOICES-LICENSE.txt`](./licenses/MIMIC3-VOICES-LICENSE.txt) و داخل بستهٔ TTS موجود است.
- منبع آرشیو: [Sherpa-ONNX TTS models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)، فایل `vits-piper-en_US-amy-medium-int8.tar.bz2`.
- مدل ONNX: `en_US-amy-medium.onnx`; SHA-256: `e93cf3361c5561b9fedcec20b26ae5ecd068172e514740562f1263be65b3848f`.
- کارت مدل و این اعلان در بستهٔ صوتی قرار می‌گیرند. متن کامل مجوز CC BY-SA 4.0 در پیوند بالا در دسترس است.

## موتور Sherpa-ONNX

- کد Java و runtime بومی Sherpa-ONNX در پروژه از [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) گرفته شده است.
- مجوز: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). اعلان و شروط مجوز را هنگام توزیع حفظ کنید.

## ترجمهٔ Google ML Kit

- وابستگی Android: `com.google.mlkit:translate:17.0.3`.
- مستندات: [Translate text with ML Kit on Android](https://developers.google.com/ml-kit/language/translation/android) و [راهنمای API ترجمه](https://developers.google.com/ml-kit/language/translation).
- شرایط: [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms). پردازش ورودی و خروجی ترجمه به‌صورت on-device است؛ شرایط Google همچنین می‌گوید SDK ممکن است گاهی برای معیارهای کارایی/استفاده، رفع اشکال و اطلاعات/به‌روزرسانی مدل به سرورهای Google وصل شود. بنابراین اپ پردازش متن را به سرور نمی‌فرستد، ولی نمی‌تواند رفتار شبکه‌ای دوره‌ای خود SDK را نفی کند.
- تنها مدل زبانی دانلودی این جفت، مدل فارسی است (حدود ۳۰MB طبق مستندات Google؛ انگلیسی داخلی است). دانلود از داخل اپ فقط بعد از تأیید کاربر و با شرط Wi‑Fi آغاز می‌شود؛ پس از نصب، ترجمه روی دستگاه کار می‌کند.
