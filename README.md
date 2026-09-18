# SIMURGH — Offline Android APK Project

این پروژه نسخه Android از فایل HTML اصلی سیمرغ است و با Capacitor 8 و Android Native ساخته می‌شود.

## مترجم فارسی ↔ انگلیسی

مترجم از Google ML Kit On-device Translation استفاده می‌کند. زبان فارسی (`fa`) و انگلیسی (`en`) پشتیبانی می‌شوند. مدل فارسی هنگام اولین فعال‌سازی با اینترنت دانلود می‌شود و بعد از نصب، ترجمه روی خود دستگاه انجام می‌شود. مدل انگلیسی در ML Kit داخلی است و دانلود جداگانه ندارد.

- فارسی → انگلیسی
- انگلیسی → فارسی
- کلمه و جمله
- بدون API ترجمه یا سرور اختصاصی
- بعد از نصب مدل فارسی، ترجمه بدون اینترنت

Google توصیه می‌کند مدل‌های ترجمه را در صورت امکان با Wi-Fi دریافت کنید؛ این پروژه دانلود را با اجازه کاربر و با اتصال موجود انجام می‌دهد.

## آیکون

فایل اصلی لوگوی ارائه‌شده در این پروژه در `branding/SIMURGH-logo-original-512x512.png` نگهداری شده و نسخه‌های Launcher در پوشه‌های `mipmap-*` قرار گرفته‌اند. Manifest نیز `ic_launcher` و `ic_launcher_round` را به عنوان آیکون برنامه معرفی می‌کند.

## ساخت در GitHub Actions

Workflow موجود در `.github/workflows/build-debug.yml` با JDK 17، Gradle 8.13 و AGP 8.13 پروژه را می‌سازد و APK را به عنوان Artifact منتشر می‌کند.

## ساخت در Termux

اگر Gradle نصب باشد:

```bash
cd android
./gradlew assembleDebug
```

اگر Gradle نصب نباشد، `android/gradlew` در صورت وجود `curl` و `unzip`، Gradle 8.13 را از آدرس رسمی Gradle دریافت و اجرا می‌کند.

## نکته درباره Build Verification

این بسته ساختار و فایل‌های پروژه را کامل دارد، اما در محیط تولید این بسته Android SDK نصب‌شده در دسترس نبود؛ بنابراین APK نهایی در همین محیط تولید نشده است. Build واقعی باید در GitHub Actions یا محیط Android/SDK اجرا شود.
