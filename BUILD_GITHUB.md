# GitHub Actions build

این پروژه برای GitHub Actions با نسخه‌های زیر تنظیم شده است:

- JDK: 17 (Temurin)
- Gradle: 8.11.1
- Android Gradle Plugin: 8.9.1
- compileSdk: 36
- targetSdk: 36
- Kotlin: 2.0.21

در این پروژه Gradle Wrapper وجود ندارد؛ بنابراین Workflow از Gradle 8.11.1 که توسط
`gradle/actions/setup-gradle` نصب می‌شود استفاده می‌کند.

Workflow در `.github/workflows/android.yml` قرار دارد.

پس از Push به `main`، GitHub Actions هر دو APK را می‌سازد و در بخش Artifacts قرار می‌دهد:

- `mahroch-client-debug`
- `mahroch-manager-debug`
