# نقشه API استخراج‌شده از سورس هاست

منبع: `assets (31).zip` ارسالی کاربر، شامل `app.js`, `teacher/app.js`, `server.mjs`.

- ورود: POST `/api/staff/login` — body: `identifier`, `password`, optional `otp`
- MFA required: HTTP 428 + `mfaRequired: true`
- حساب جاری: GET `/api/staff/me`
- داشبورد: GET `/api/teacher/dashboard`
- workspace: GET `/api/teacher/workspace`
- حضور: POST `/api/teacher/attendance/submit` — `scheduleEntryId`, `presentStudentIds`
- دفتر نمره: GET `/api/staff/gradebook?classId=...&subject=...&academicPeriod=...`
- ثبت نمره: POST `/api/staff/gradebook/entry`
- پیام‌ها: GET contacts/threads و POST send در `/api/academic-messages/p58/*`
- تمرین‌ها: GET `/api/practice/p59/list`
- کتابخانه: GET `/api/library/p62/list`
- نشست‌ها: GET `/api/staff/sessions`

API base در حالت hosted برابر base path برنامه است (`/app`)؛ بنابراین API نهایی روی دامنه فعلی به شکل `https://scooljavanrood.ir/app/api/...` فراخوانی می‌شود.
