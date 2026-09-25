# Mahroch Dual Apps v4 — Managed Always-on VPN

این نسخه دو APK جدا دارد:

- `Mahroch Client`
- `Mahroch Manager`

## قابلیت مهم این نسخه

برای گوشی‌های سازمانی/تحت مدیریت، Client یک `DeviceAdminReceiver` دارد تا پس از
Provision شدن به عنوان **Device Owner** بتواند Always-on VPN را از طریق
`DevicePolicyManager` تنظیم کند.

در این حالت خاموش کردن VPN از رابط معمولی گوشی نباید سیاست Always-on را به حالت
عادی برگرداند؛ Android مدیریت اتصال VPN را انجام می‌دهد.

## مشخصات شبکه

SSID: `HSC_mahroch-2101`
Gateway: `192.168.33.1`
Subnet: `255.255.255.0`
DNS: `8.8.8.8`

## نکته بسیار مهم درباره Device Owner

یک اپ معمولی Android نمی‌تواند خودش و بدون تأیید سیستم، Device Owner شود.
برای تست روی دستگاه آزمایشی می‌توان قبل از اضافه‌شدن حساب کاربری، دستگاه را با
ADB به صورت Device Owner provision کرد:

```bash
adb shell dpm set-device-owner com.mahroch.client/.MahrochDeviceAdminReceiver
```

بعد از provisioning، داخل Client روی:

`فعال‌سازی مدیریت دائمی (Device Owner)`

بزن.

برای دستگاه‌هایی که قبلاً حساب/مدیریت دیگری دارند، ممکن است لازم باشد ابتدا دستگاه
را از مدیریت قبلی خارج یا برای تست factory reset شود.

## Build

در Android Studio پروژه را Open و Gradle Sync کن.

APK Client:
`client/build/outputs/apk/debug/client-debug.apk`

APK Manager:
`manager/build/outputs/apk/debug/manager-debug.apk`

## درباره خاموش کردن توسط کاربر

Always-on/lockdown باید توسط Android و Device Owner اعمال شود. یک اپ معمولی،
بدون Device Owner، اجازه ندارد تضمین کند که کاربر VPN را خاموش کرد بلافاصله آن را
مخفیانه دوباره روشن کند.

این پروژه مسیر رسمی Android برای دستگاه‌های تحت مدیریت را آماده کرده است.

## وضعیت موتور شبکه

Client از `VpnService` و موتور userspace/tun2socks استفاده می‌کند. برای فیلترینگ
دامنه‌ای، مقصدهای IP مستقیم باید رد شوند و DNS نیز نباید مسیر دورزدن سیاست باشد.
برای production بهتر است native engine به صورت AAR/ABIهای arm64-v8a و armeabi-v7a
داخل پروژه pin و تست شود.


## Client visibility

در نسخه v5 برای `Mahroch Client` هیچ `MAIN/LAUNCHER` activity ثبت نشده است؛
بنابراین آیکون Client در لیست معمول برنامه‌های قابل اجرا نمایش داده نمی‌شود.
`Mahroch Manager` همچنان Launcher دارد و در لیست برنامه‌ها باقی می‌ماند.

Client برای شروع VPN در این نسخه از سرویس/Receiver استفاده می‌کند.
اولین اجازه VPN و provisioning مربوط به Device Owner همچنان باید توسط Android
و مدیر دستگاه انجام شود.


## ارتباط Manager و Client

Client یک کنترل‌سرور LAN روی پورت `8765` دارد. Manager از همین مسیر برای تست اتصال و ارسال سیاست استفاده می‌کند:

- تست: `GET /health`
- ارسال سیاست: `POST /policy`
- احراز هویت با `X-Mahroch-Token` یا فیلد `token`
- کد اتصال پیش‌فرض فعلی: `mahroch-2101` (قابل تغییر در `Policy.kt`)

در Manager، IP باید **IP محلی همان گوشی‌ای باشد که Mahroch Client روی آن نصب است**؛ Gateway روتر (`192.168.33.1`) نباید وارد شود.

برای HTTP داخل شبکه محلی، Manager اجازه Cleartext را به صورت صریح فعال کرده است.
