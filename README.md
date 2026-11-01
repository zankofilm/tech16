
## رفتار MFA مطابق هاست (3.0.2)

- مرحله اول: نام کاربری + رمز عبور.
- در صورت `MFA_REQUIRED`: فقط پنجره کد Authenticator باز می‌شود.
- نام کاربری و رمز مرحله اول پشت صحنه برای درخواست دوم استفاده می‌شوند و دوباره از کاربر پرسیده نمی‌شوند.
- در صورت اجازه سرور، کد بازیابی نیز قابل استفاده است.

# مدرسه‌یار معلم — Direct API v3.0.1

## اصلاح MFA
- ورود مرحله اول فقط با نام کاربری و رمز عبور انجام می‌شود.
- در صورت فعال بودن MFA، صفحه جدا فقط کد ۶ رقمی Authenticator را می‌گیرد.
- نام کاربری/رمز مرحله اول فقط موقتاً در حافظه همان اجرای اپ نگه داشته می‌شود و کاربر دوباره آن‌ها را وارد نمی‌کند.
- در صورت فعال بودن Recovery Codes، گزینه کد بازیابی نیز موجود است.
- هیچ WebView یا نسخه وب در فرآیند ورود وجود ندارد.

# مدرسه‌یار معلم — Android Direct API v3.0.0

این نسخه WebView/DOM scraping ندارد. رابط Android با Jetpack Compose ساخته شده و مستقیماً به API واقعی سورس هاست متصل می‌شود.

## Base URL
پیش‌فرض: `https://scooljavanrood.ir/app/`

طبق سورس هاست، `SERVER_BASE` همان base path برنامه است و endpointها زیر آن قرار می‌گیرند؛ برای مثال:
- `/app/api/staff/login`
- `/app/api/teacher/dashboard`
- `/app/api/teacher/workspace`

## قابلیت‌های Direct API این Build
- ورود معلم با `/api/staff/login`
- MFA با همان endpoint و `otp`
- بررسی نشست `/api/staff/me`
- تغییر رمز اجباری `/api/staff/change-password`
- داشبورد `/api/teacher/dashboard`
- کلاس‌ها و دانش‌آموزان `/api/teacher/workspace`
- حضور و غیاب `/api/teacher/attendance/submit`
- دفتر نمره GET `/api/staff/gradebook`
- ثبت نمره POST `/api/staff/gradebook/entry`
- پیام‌ها و مخاطبان `/api/academic-messages/p58/*`
- تمرین‌ها `/api/practice/p59/list`
- کتابخانه `/api/library/p62/list`
- نشست‌های حساب `/api/staff/sessions`
- خروج `/api/staff/logout`
- ورود اثر انگشتی با توکن رمزنگاری‌شده در Android Keystore

## حذف کامل WebView
فایل‌های `NativeBridgeActivity.kt`, `native-bridge.js`, `auth-bridge.js` و dependency مربوط به `androidx.webkit` در این نسخه وجود ندارند.

## Build
GitHub Actions -> `Build Madreseyar Teacher APK`
