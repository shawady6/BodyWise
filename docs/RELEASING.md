# إصدار تحديث جديد / Releasing

1. غيّر `versionCode` (زوّده 1 على الأقل) و`versionName` في `app/build.gradle.kts`.
   - الـ tag بيتعمل تلقائياً بالشكل `v<versionName>`، ولو الـ tag موجود الـ workflow بيفشل عمداً.
2. اعمل commit على `main` واكتب `[release]` في رسالة الـ commit.
   - من غير `[release]` الـ workflow بيبني ويتحقق من التوقيع بس، ومبينشرش Release.
   - بديل: شغّل الـ workflow يدوياً (workflow_dispatch) من تبويب Actions.
3. الـ workflow بيعمل بالترتيب: بناء Debug ← فحص الـ Secrets ← فتح الـ Keystore ← فحص المفتاح الخاص ← `assembleRelease` ← `apksigner verify` ← SHA-256 ← tag ← Release ← التأكد من وجود الـ APK وملف `.sha256`.
4. بعد النشر راجع صفحة الـ Release وتأكد إن `app-release.apk` و`app-release.apk.sha256` موجودين.

## التوقيع
- نفس الـ Keystore الدائم لكل الإصدارات. بصمة الشهادة (SHA-256) لإصدار v0.2.5:
  `d2b90dbd383394c163214526b2386bb3bf6bd6ed7adeccb14e3c8ac2e74db50c`
- الـ Secrets المطلوبة: `BODYWISE_KEYSTORE_BASE64`, `BODYWISE_STORE_PASSWORD`, `BODYWISE_KEY_ALIAS`, `BODYWISE_KEY_PASSWORD`.
- الـ Keystore نوعه PKCS12، فكلمة مرور المفتاح لازم تساوي كلمة مرور الـ store.
- ممنوع تغيير الـ Keystore أو رفعه على GitHub، وممنوع طباعة أي كلمة مرور في اللوج.
