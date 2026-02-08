# הרע☠︎ במיעוטו⚕︎

שימוש ב‑Android DevicePolicyManager API כדי לנהל את המכשיר שלך. [file:1]

## הורדה

- [מאגר IzzyOnDroid F-Droid](https://apt.izzysoft.de/fdroid/index/apk/com.bintianqi.owndroid) [file:1]
- [גרסאות ב‑GitHub](https://github.com/BinTianqi/OwnDroid/releases) [file:1]

> [!NOTE]
> משתמשי ColorOS צריכים להוריד את גרסת ה‑testkey מה‑releases ב‑GitHub. [file:1]

## יכולות

- מערכת: השבתת מצלמה, השבתת צילום מסך, השתקת עוצמת קול ראשית, השבתת אות USB, מצב נעילת משימה (lock task mode), מחיקת נתונים וכו'. [file:1]
- רשת: הוספה/עריכה/מחיקה של רשתות Wi‑Fi, סטטיסטיקות רשת, לוגים של רשת וכו'. [file:1]
- אפליקציות: השעיה/הסתרה של אפליקציות, חסימת הסרת התקנה, מתן/שלילת הרשאות, ניקוי אחסון אפליקציה, התקנה/הסרה של אפליקציה וכו'. [file:1]
- הגבלות משתמש: השבתת SMS, השבתת שיחות יוצאות, השבתת Bluetooth, השבתת NFC, השבתת העברת קבצים דרך USB, השבתת התקנה/הסרה של אפליקציות וכו'. [file:1]
- משתמשים: מידע על משתמש, יצירה/הפעלה/החלפה/עצירה/מחיקה של משתמש וכו'. [file:1]
- סיסמה ונעילת מסך: איפוס סיסמה, הגדרת זמן כיבוי מסך וכו'. [file:1]

## מצבי עבודה

- Device owner (מומלץ) [file:1]

  שיטות הפעלה: [file:1]
  - Shizuku [file:1]
  - Dhizuku [file:1]
  - Root [file:1]
  - פקודת ADB shell: `dpm set-device-owner com.bintianqi.owndroid/.Receiver` [file:1]

- [Dhizuku](https://github.com/iamr0s/Dhizuku) [file:1]
- Work profile [file:1]

## שאלות נפוצות (FAQ)

### כבר קיימים חשבונות במכשיר

```text
java.lang.IllegalStateException: Not allowed to set the device owner because there are already some accounts on the device
``` [file:1]

פתרונות: [file:1]
- להקפיא אפליקציות שמחזיקות את החשבונות האלו. [file:1]
- למחוק את החשבונות האלו. [file:1]

### כבר קיימים מספר משתמשים במכשיר

```text
java.lang.IllegalStateException: Not allowed to set the device owner because there are already several users on the device
``` [file:1]

פתרונות: [file:1]
- למחוק משתמשים משניים. [file:1]

> [!NOTE]
> בחלק מהמערכות יש פיצ’רים כמו שיבוט אפליקציות (app cloning) ומרחב ילדים, שבדרך כלל ממומשים כמשתמשים נוספים. [file:1]

### Device owner כבר מוגדר

```text
java.lang.IllegalStateException: Trying to set the device owner (com.bintianqi.owndroid/.Receiver), but device owner (xxx) is already set.
``` [file:1]

יכול להיות רק Device owner אחד במכשיר. נא להסיר קודם את ה‑device owner הקיים. [file:1]

### MIUI & HyperOS

```text
java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_DEVICE_ADMINS.
``` [file:1]

פתרונות: [file:1]
- להפעיל `USB debugging (Security setting)` באפשרויות המפתח. [file:1]
- או להריץ את פקודת ההפעלה ב‑root shell. [file:1]

### ColorOS

```text
java.lang.IllegalStateException: Unexpected @ProvisioningPreCondition
``` [file:1]

פתרון: להשתמש בגרסת ה‑testkey של OwnDroid. [file:1]

### Samsung

```text
user limit reached
``` [file:1]

Samsung מגבילה את פיצ’ר ריבוי המשתמשים של אנדרואיד ואין כרגע פתרון. [file:1]

## API

OwnDroid מספק ממשק API מבוסס Intents. [file:1] יש להגדיר מפתח API בהגדרות ולהפעיל את ה‑API. [file:1] המספרים בסוגריים מציינים את גרסת האנדרואיד המינימלית הנדרשת. [file:1]

- HIDE(package: String) [file:1]
- UNHIDE(package: String) [file:1]
- SUSPEND(package: String) (7) [file:1]
- UNSUSPEND(package: String) (7) [file:1]
- ADD_USER_RESTRICTION(restriction: Boolean) [file:1]
- CLEAR_USER_RESTRICTION(restriction: Boolean) [file:1]
- SET_PERMISSION_DEFAULT(package: String, permission: String) (6) [file:1]
- SET_PERMISSION_GRANTED(package: String, permission: String) (6) [file:1]
- SET_PERMISSION_DENIED(package: String, permission: String) (6) [file:1]
- SET_SCREEN_CAPTURE_DISABLED() [file:1]
- SET_SCREEN_CAPTURE_ENABLED() [file:1]
- SET_CAMERA_DISABLED() [file:1]
- SET_CAMERA_ENABLED() [file:1]
- SET_USB_DISABLED() (12) [file:1]
- SET_USB_ENABLED() (12) [file:1]
- LOCK() [file:1]
- REBOOT() (7) [file:1]

```shell
# דוגמה להסתרת אפליקציה מ-ADB shell
am broadcast -a com.bintianqi.owndroid.action.HIDE -n com.bintianqi.owndroid/.ApiReceiver --es key abcdefg --es package com.example.app
``` [file:1]

```kotlin
// דוגמה להסתרת אפליקציה ב-Kotlin
val intent = Intent("com.bintianqi.owndroid.action.HIDE")
    .setComponent(ComponentName("com.bintianqi.owndroid", "com.bintianqi.owndroid.ApiReceiver"))
    .putExtra("key", "abcdefg")
    .putExtra("package", "com.example.app")
context.sendBroadcast(intent)
``` [file:1]

[רשימת הגבלות משתמש זמינות](https://developer.android.com/reference/android/os/UserManager#constants_1) [file:1]

## Build

ניתן לבנות את OwnDroid בעזרת Gradle משורת הפקודה. [file:1]

```shell
# שימוש ב-testkey לחתימה (ברירת מחדל)
./gradlew build

# שימוש במפתח .jks מותאם אישית לחתימה
./gradlew build -PStoreFile="/path/to/your/jks/file" -PStorePassword="YOUR_KEYSTORE_PASSWORD" -PKeyPassword="YOUR_KEY_PASSWORD" -PKeyAlias="YOUR_KEY_ALIAS"
``` [file:1]

(ב‑Windows יש להשתמש ב‑`./gradlew.bat` במקום.) [file:1]

## רישיון

[License.md](LICENSE.md) [file:1]

> Copyright (C)  2026  BinTianqi
>
> תוכנה זו היא חופשית: ניתן להפיץ אותה מחדש ו/או לשנות אותה תחת תנאי הרישיון הציבורי הכללי של GNU כפי שפורסם על ידי Free Software Foundation, גרסה 3 של הרישיון או (לבחירתך) כל גרסה מאוחרת יותר. [file:1]
>
> תוכנה זו מופצת בתקווה שהיא תהיה שימושית, אך ללא כל אחריות; אפילו ללא אחריות משתמעת של סחירות או התאמה למטרה מסוימת. לפרטים נוספים ראה את הרישיון הציבורי הכללי של GNU. [file:1]
>
> אמור היית לקבל עותק של הרישיון הציבורי הכללי של GNU יחד עם תוכנה זו. אם לא, ראה <https://www.gnu.org/licenses/>. [file:1]
