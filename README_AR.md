<p align="center">
  <img src="docs/screenshots/launcher.png" width="120" alt="أيقونة ماوي">
</p>

<h1 align="center">ماوي Maui</h1>
<p align="center"><b>نظام فوق أندرويد — بيشتغل على جهازك فعلاً</b></p>
<p align="center">
  لوحة تحكم عائمة · شاشة أداء مباشرة · شريط للألعاب · تنظيف يكشف الملفات المخفية والمكررة · تحسين ذاكرة وألعاب بأرقام حقيقية
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="#arabic">العربية</a> ·
  <a href="#license">رخصة MIT</a>
</p>

---

<p align="center">
  <img src="docs/screenshots/dashboard.png" width="260" alt="شاشة الرئيسية">
  <img src="docs/screenshots/cleaner.png" width="260" alt="شاشة التنظيف">
  <img src="docs/screenshots/layers.png" width="260" alt="طبقة ماوي">
</p>

> **كل أرقام ماوي مقيسة، ولا فيها أي رقم متخيّل.** الذاكرة والمعالج والحرارة ومعدل الإطارات
> تُقرأ مباشرة من نظام التشغيل (`/proc/stat`، `ActivityManager.MemoryInfo`، ملفات thermal في
> `/sys`، و`SurfaceFlinger`)، والمساحة المحرّرة تُقاس قبل وبعد الحذف فعلياً. وإذا أي ميزة
> تحتاج Root، التطبيق بيقولك بصراحة بدل ما يدّعي إنه عملها.

---

<a id="arabic"></a>🇪🇬 العربية

### شو هو ماوي؟

**ماوي** تطبيق أندرويد كامل بمزايا كتير، بيضيف **طبقة نظام فوق أندرويد** على طريقة Samsung
One UI: مقبض (فقاعة) عايم تفتح منه لوحة تحكم فوق أي تطبيق، وشاشة أداء مباشرة، وشريط ألعاب
بيظهر لحاله لحظة ما يتعرف على لعبة شغّالة.

وكمان فيه أدوات التنظيف وتحسين الأداء اللي الناس متوقعها — بس مبنية على واجهات النظام
الحقيقية، مش عدّادات متحركة.

### ✨ المزايا

#### 🫧 طبقة ماوي — النظام فوق أندرويد
| | |
|---|---|
| **الفقاعة العائمة** | مقبض `M` بتسحبه وبيتثبّت على حافة الشاشة مع نبض خفيف. ضغطة طويلة بتوقّف الطبقة. |
| **لوحة التحكم** | بتنفتح كـ sheet فيها أرقام حيّة: الذاكرة، المعالج، الحرارة، البطارية، الشبكة + أزرار بنقرة: تنظيف، تحسين، ملفات، تطبيقات، إعدادات. |
| **شاشة الأداء (HUD)** | إطار/ثانية، الذاكرة، المعالج، الحرارة، البطارية وسرعة الشبكة، فوق **أي** تطبيق. |
| **شريط الألعاب** | بيظهر تلقائياً لما تكتشف لعبة في المقدمة — إطار/ثانية، تحسين، تنظيف، خروج، من غير ما تطلع من الماتش. |
| **يحفظ مكانه** | حجم الفقاعة وشفافيتها وموضعها وحوافتها بتضل محفوظة بعد إعادة التشغيل، والطبقة بترجع لوحدها بعد البوت. |

#### 🧹 التنظيف — بيلاقي الملفات اللي ما بتلاقيها
- **كاش التطبيقات، الصور المصغرة، الملفات المؤقتة، السجلات، المجلدات الفاضية** — مفحوصة ومجمّعة حسب النوع.
- **الملفات المخفية** — الملفات والمجلدات اللي تبدأ بنقطة (`.`) في أي مكان بالتخزين المشترك.
- **الملفات الكبيرة** — كل شي أكبر من 100 ميجابايت، مرتب من الأكبر للأصغر.
- **المكررة بايت ببايت** — الملفات بتتجمّع بالحجم وبعدها بيتأكد التطابق بـ MD5، فبتنرّفع بس المكررة الحقيقية (والأصل ما بينمس).
- **ملفات `.apk` قديمة** فاضلة في مجلد التحميلات.
- **آمن افتراضياً** — بس الأنواع الآمنة بتفعّل تلقائياً. الملفات المخفية والكبيرة والمكررة والمثبّتات لازم تحددها انت بإيدك.
- **تقرير أمين** — المساحة المحرّرة بتتقاس، مش بتتقدّر.

#### ⚡ تحسين الذاكرة ووضع الألعاب
- **حسّن الآن** بيقتل العمليات الخلفية عبر `ActivityManager.killBackgroundProcesses` وبيقصّ كاش النظام بـ `pm trim-caches` (لما يكون فيه Root)، وبعدين بيقولك التغيّر **الفعلي** في الذاكرة المتاحة.
- **وضع الألعاب** بيصفّر سرعة الأنيميشن والـ transitions (أكبر مكسب حقيقي ضد التقطيع)، بيقتل التطبيقات الخلفية، بيفعّل شريط الألعاب، وبيكتم الإشعارات عبر Do Not Disturb.
- **إطار/ثانية حقيقي** — من `SurfaceFlinger` لما يتوفر Root، ومن الـ frame callback غير كده.

#### 📂 الملفات و 📦 التطبيقات
- متصفح ملفات مدمج: التخزين الداخلي، كارت SD، المخفي فقط، الكبير فقط، المكررة فقط — مع حذف (بتأكيد) ومشاركة عبر `FileProvider`.
- مدير تطبيقات بتفصيل **تطبيق / كاش / بيانات** لكل تطبيق من `StorageStatsManager`، مع بحث وترتيب بالحجم أو بالاسم، وإيقاف قسري، ومسح كاش، وإزالة.
- إجراءات Root بتتفتح تلقائياً لما يت detect الـ `su`: `pm trim-caches`، مسح كاش تطبيق معيّن، `am force-stop`، وفحص الكاش بـ `du`.

#### 🌍 عربي وإنجليزي
- واجهة كاملة بالعربية (`values-ar`) مع دعم RTL، واسم التطبيق على المشغّل `ماوي`.
- ثيم Material 3، فاتح / داكن / حسب النظام.

### 📦 التثبيت

الـ APK موقّع واسمه `Maui-v1.0-release.apk`.

**أولاً — من إصدارات GitHub (المفضّل).** صفحة الإصدارات فيها الـ APK كـ release asset حقيقي:

    https://github.com/YOUSSEFJEDIDI89/Maui/releases

**ثانياً — النسخة المطابقة داخل المستودع.** نفس الـ APK متخزّن هنا base64 مقطّعات، وبتقدر
تعيد بناءه بايت ببايت:

```bash
git clone https://github.com/YOUSSEFJEDIDI89/Maui.git
cd Maui/release
cat Maui-v1.0-release.apk.b64.* | base64 -d > Maui-v1.0-release.apk
sha256sum Maui-v1.0-release.apk
# 8c2adb5794aa885b235680bba94de2e18c077c1d67f82666e93e55f3bac518b0
```

وبعدين على الموبايل: افتح الـ APK، واسمح بـ "تثبيت من مصادر غير معروفة"، وثبّته، وافتح **ماوي**.

عن طريق ADB:

```bash
adb install -r Maui-v1.0-release.apk
```

### 🔁 نشر إصدار جديد

الإصدار بيتبني عن طريق CI (`ci/release.yml`): بيبني الـ APK موقّع، بيشغّل الاختبارات، بيعيد
توليد الصور، وبيعلّق الـ APK على GitHub Release.

```bash
bash scripts/enable_release.sh          # يفعّل الـ workflow (مرة واحدة)
gh workflow run release.yml -f version=v1.0
```

سكربت `scripts/enable_release.sh` بينسخ الـ workflow جوّه `.github/workflows/` وبيعمله push.
وبعدها الإصدار ممكن يتشغّل مباشرة من تاب Actions.

### 🔐 الصلاحيات — وليش كل واحدة بالظبط

ماوي بيشتغل **بدون Root** عبر واجهات أندرويد العامة، وكمان بيكشف `su` تلقائياً وبيفتح
صلاحيات إضافية لما يكون موجود.

| الصلاحية | ليش | بدونها |
|---|---|---|
| العرض فوق التطبيقات | عشان ترسم فقاعة ماوي | الطبقة مش هتظهر |
| الوصول لجميع الملفات | لفحص وحذف المهمل بالتخزين المشترك | تنظيف كاش التطبيق بس |
| إذن الاستخدام | كشف التطبيق الأمامي، مساحة كل تطبيق، الألعاب | مفيش شريط ألعاب ولا عمود المساحة |
| الإشعارات | إشعار "طبقة ماوي شغّالة" | الخدمة بتشتغل بس صامتة |
| استثناء من توفير البطارية | عشان الطبقة ما تتقتل | أندرويد ممكن يوقفها |
| تعديل إعدادات النظام | ضبط سرعة الأنيميشن في وضع الألعاب | الوضع بيشتغل بدون تعديل الأنيميشن |
| إذن Do Not Disturb | كتم الإشعارات أثناء اللعب | الـ DND بيتخطى والباقي شغّال |
| `QUERY_ALL_PACKAGES` | عشان يشوف كل التطبيقات المثبّتة | قائمة التطبيقات ناقصة |

كل الصلاحيات موجودة في **الإعدادات ← الصلاحيات** مع حالة حيّة لكل واحدة وزر **منح** بيفتح
شاشة النظام المناسبة مباشرة.

### 🛠 البناء من المصدر

```bash
git clone https://github.com/YOUSSEFJEDIDI89/Maui.git
cd Maui
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:testReleaseUnitTest  # اختبارات JVM + Robolectric
```

المتطلبات: **JDK 17**، **Android SDK 35** (منصة 35 + build-tools 35.0.0)، Gradle 8.9+.

> المستودع فيه **مفتاح توقيع تجريبي** (`keystore/maui-release.jks`، كلمة المرور `maui2026`،
> الاسم `maui`) عشان الـ CI يطلع APK قابل للتثبيت لأي حد يعمل Fork. غيّره بمفتاحك قبل النشر.

### 🧪 الاختبارات

```bash
./gradlew :app:testReleaseUnitTest
```

* `CoreLogicTest` — تنسيق الأحجام والنِسب، قياس أحجام المجلدات، وتنفيذ أوامر shell حقيقية.
* `MauiSmokeTest` — **Robolectric** بيشغّل كل الشاشات على رَن تايم أندرويد حقيقي: الشاشة الرئيسية وتاباتها الخمسة، بلاطات الأداء، صفوف الصلاحيات، شاشتي التطبيقات والمستكشف، والـ overlays الأربعة، واختبار للماسح بيزرع ملفات مهملة على القرص وبيأكد إنها انكشفت.

### 📁 هيكل المشروع

```
Maui/
├── app/src/main/java/com/maui/system/
│   ├── core/       DeviceProfiler · PerformanceSampler · AppHelper · Cleaner · RootShell · Prefs
│   ├── scanner/    StorageScanner — مهمل، مخفي، ملفات كبيرة، مكررة
│   ├── overlay/    MauiOverlayService · BubbleView · MauiPanelView · GameToolbarView · HudView
│   └── ui/         MainActivity · Dashboard/Cleaner/Files/Games · Settings · Apps · FileExplorer
├── app/src/main/res/     تخطيطات، values (إنجليزي)، values-ar (عربي)، ثيم Material 3
├── app/src/test/         اختبارات JVM + Robolectric
├── docs/screenshots/     بيتولّد من الـ CI من التطبيق الحقيقي
└── tools/make_icons.py   مولّد أيقونة المشغّل
```

### ⚖️ حدود واقعية

- مسح كاش التطبيقات **الأخرى** محتاج Root، أو مسحه من إعدادات النظام. ماوي بيقولك كده بدل ما يدّعي إنه عملها.
- قراءة مساحة كل تطبيق محتاجة إذن الاستخدام؛ بدونه القائمة شغّالة بس عمود المساحة فاضي.
- الطبقة محتاجة "العرض فوق التطبيقات"، وبعض المشغّلات بيعيد تشغيل النشاط الأمامي بعد المنح، فالفقاعة بتختفي لحظة وبترجع.

---

<a id="license"></a>

## 📄 الرخصة

**رخصة MIT** — استخدمه، اعمله Fork، عدّله، وانشره بحرية. شوف [LICENSE](LICENSE).

```
MIT License

Copyright (c) 2026 Maui Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
