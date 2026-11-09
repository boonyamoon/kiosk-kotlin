# Kiosk

แอป Android สำหรับตู้สั่งอาหารด้วยตัวเองของระบบ MeeD POS ลูกค้าเลือกเมนู ชำระเงิน และรับใบเสร็จได้เองที่หน้าตู้ แอปทำงานคู่กับ `pos-services` (backend) และ iPad POS (`myapp`)

## ความสามารถหลัก

- **ขั้นตอนลูกค้า:** หน้าแรก → สมาชิก → เมนู → ตะกร้า → ชำระเงิน → หน้าสำเร็จและใบเสร็จ
- **ชำระเงิน:** เงินสดที่เคาน์เตอร์ หรือ QR พร้อมเพย์ผ่าน Beam
- **เครื่องพิมพ์ใบเสร็จ:** LAN, เครื่องพิมพ์ในตัวของ Sunmi และ iMin, เครื่องพิมพ์ USB
- **ล็อกหน้าจอ:** ปักหมุดแอป (app pinning) หรือ device owner
- **เมนูผู้ดูแล:** แตะโลโก้หรือชื่อร้านที่หน้าแรก 5 ครั้งภายใน 3 วินาที แล้วใส่ PIN พนักงาน
- **ภาษา:** ไทยและอังกฤษ

## สิ่งที่ต้องมี

| เครื่องมือ | เวอร์ชัน |
|---|---|
| JDK | 17 |
| Android SDK | Platform 36 |
| Gradle | ใช้ `./gradlew` ที่มากับโปรเจกต์ (8.14) ไม่ต้องติดตั้งเอง |
| เครื่องที่รันแอป | Android 8.0 (API 26) ขึ้นไป |

**JDK ต้องเป็นเวอร์ชัน 17** JDK ที่มากับ Android Studio รุ่นใหม่อาจเป็น Java 25 ซึ่ง Gradle 8.14 ไม่รองรับ

```sh
# macOS
brew install openjdk@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

ถ้า build จาก Android Studio ให้เลือก JDK 17 ที่ Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK

**Android SDK:** Android Studio สร้าง `local.properties` ให้เอง ถ้า build จาก command line อย่างเดียว ให้ตั้งค่าอย่างใดอย่างหนึ่ง

```sh
export ANDROID_HOME=$HOME/Library/Android/sdk
# หรือสร้างไฟล์ local.properties แล้วใส่ sdk.dir=/Users/<you>/Library/Android/sdk
```

## Build สำหรับพัฒนา (debug)

```sh
./gradlew :app:assembleDebug    # ได้ไฟล์ app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug     # build แล้วติดตั้งลงเครื่องที่ต่ออยู่
```

ถ้าต่อหลายเครื่อง ให้เลือกเครื่องด้วย `ANDROID_SERIAL` ไม่เช่นนั้นจะติดตั้งทุกเครื่อง

```sh
adb devices
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
```

### ตั้ง URL ของ backend

URL ถูกฝังในแอปตอน build จากค่า `kioskApiBaseUrl` ใน `gradle.properties` เปลี่ยนค่าแล้วต้อง build ใหม่ และ URL ต้องลงท้ายด้วย `/`

| แอปรันบน | `kioskApiBaseUrl` | หมายเหตุ |
|---|---|---|
| Emulator | `http://10.0.2.2:8080/` | `10.0.2.2` คือเครื่องคอมที่รัน emulator |
| เครื่องจริงเสียบ USB | `http://localhost:8080/` | รัน `adb reverse tcp:8080 tcp:8080` ก่อน |
| เครื่องจริงผ่าน Wi-Fi | `http://<IP เครื่องคอม>:8080/` | ต้องอยู่ในวง Wi-Fi เดียวกัน |
| Production | `https://api.meedeepos.com/` | |

ส่งค่าเฉพาะครั้งได้ด้วย `-P` โดยไม่ต้องแก้ไฟล์

```sh
./gradlew :app:installDebug -PkioskApiBaseUrl=http://10.0.2.2:8080/
```

URL แบบ `http://` ใช้ได้เฉพาะ debug ถ้าเปลี่ยน backend แล้วแอปยังใช้ไม่ได้ อาจต้องผูกเครื่องใหม่จากเมนูผู้ดูแล

## Build สำหรับติดตั้งจริง (release)

### 1. เตรียมกุญแจ sign (ทำครั้งเดียว)

```sh
mkdir -p ~/keystores
keytool -genkeypair -v -keystore ~/keystores/kiosk-upload.jks -alias kiosk -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties
```

จากนั้นแก้ `keystore.properties` โดย `storeFile` ต้องเป็น path เต็ม ใช้ `~` ไม่ได้

```properties
storeFile=/Users/<you>/keystores/kiosk-upload.jks
storePassword=รหัสผ่าน keystore
keyAlias=kiosk
keyPassword=รหัสผ่านกุญแจ
```

> **เก็บไฟล์ `.jks` และรหัสผ่านให้ดี และห้าม commit** ทั้งสองอย่างอยู่ใน `.gitignore` แล้ว เครื่องที่ติดตั้ง APK เองจะอัปเดตทับได้ก็ต่อเมื่อใช้กุญแจเดิมเท่านั้น

### 2. ขึ้นเลขเวอร์ชัน

แก้ `app/build.gradle.kts` โดยเพิ่ม `versionCode` ทุกครั้งที่ปล่อยเวอร์ชันใหม่ และตั้ง `versionName` ตามเลขที่จะแสดงให้ผู้ใช้เห็น

### 3. Build

เลือกตามช่องทางที่จะปล่อย

```sh
# Google Play
./gradlew :app:bundleRelease -PkioskApiBaseUrl=https://api.meedeepos.com/

# ติดตั้งเอง หรืออัปโหลดเข้า store ของ Sunmi และ iMin
./gradlew :app:assembleRelease -PkioskApiBaseUrl=https://api.meedeepos.com/
```

| ไฟล์ที่ได้ | ใช้กับ |
|---|---|
| `app/build/outputs/bundle/release/app-release.aab` | อัปโหลดขึ้น Google Play Console |
| `app/build/outputs/apk/release/app-release.apk` | ติดตั้งเอง และเครื่องที่ไม่มี Google Play |

ติดตั้ง APK ลงเครื่องด้วย adb

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

ถ้าเครื่องนั้นมีตัว debug อยู่ ต้องถอนก่อนด้วย `adb uninstall com.pos.kiosk` แล้วผูกเครื่องใหม่ เพราะทั้งสองตัวใช้ package เดียวกันแต่คนละกุญแจ

ข้อกำหนดของ release build:

- URL ต้องขึ้นต้นด้วย `https://` และห้ามเป็น `localhost` หรือ `10.0.2.2` ไม่เช่นนั้น build จะหยุดเอง
- ถ้าไม่มี `keystore.properties` จะได้ `app-release-unsigned.apk` ซึ่งติดตั้งไม่ได้

## เริ่มใช้งานเครื่องใหม่

1. ฝั่ง backend ร้านต้องมีสิทธิ์ `enable_kiosk` และสาขาต้องมี terminal ของ Kiosk
2. เปิดแอป เข้าสู่ระบบด้วยบัญชีร้าน เลือกสาขาและ terminal แล้วกดผูกเครื่อง
3. ตั้งล็อกหน้าจอตาม [คู่มือตั้งค่าเครื่อง Kiosk](docs/kiosk-device-setup.md)
4. เข้าเมนูผู้ดูแล แล้วกดทดสอบพิมพ์

## เครื่องพิมพ์ใบเสร็จ

แอปเลือกเครื่องพิมพ์ตามลำดับนี้

1. เครื่องพิมพ์ LAN ที่กำหนดให้เครื่อง Kiosk นี้บน iPad
2. เครื่องพิมพ์ในตัวของ Sunmi หรือ iMin
3. เครื่องพิมพ์ที่เสียบ USB ไว้ โดยใช้กับเครื่องที่ไม่มีเครื่องพิมพ์ในตัวเท่านั้น
4. ถ้าไม่มีเลย แอปจะบอกให้ลูกค้าจดหมายเลขคิว

ใบเสร็จถูกพิมพ์เป็นภาพ เพื่อให้สระและวรรณยุกต์ไทยแสดงถูกต้อง LAN และ USB ส่งคำสั่ง ESC/POS โดยตรง จึงไม่ใช้ SDK ของผู้ผลิตเครื่องพิมพ์ ส่วนเครื่องพิมพ์ในตัวใช้ SDK ของ Sunmi และ iMin

รายละเอียดรุ่นที่รองรับและการตั้งค่า USB อยู่ใน[คู่มือตั้งค่าเครื่อง Kiosk](docs/kiosk-device-setup.md)

## ทดสอบ

```sh
./gradlew :app:testDebugUnitTest    # รายงาน: app/build/reports/tests/testDebugUnitTest/index.html
./gradlew :app:lintDebug            # รายงาน: app/build/reports/lint-results-debug.html
```

Baseline profile เป็นไฟล์ที่ commit ไว้แล้ว ไม่ต้องสร้างใหม่ทุกครั้ง วิธีสร้างใหม่และวัดผลอยู่ที่ [baselineprofile/README.md](baselineprofile/README.md)

## โครงสร้างโปรเจกต์

ใช้ Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit, OkHttp, DataStore และ Coil จัดแบบ MVVM + Clean Architecture

```text
app/
├── libs/                    SDK ของ iMin
└── src/main/java/com/pos/kiosk/
    ├── core/                ส่วนกลาง: เครื่องพิมพ์, session, network, storage, UI
    ├── data/                เรียก API และ repository
    ├── domain/              model และ use case
    ├── feature/             หน้าจอ: attract, member, menu, cart, payment, success, setup, admin
    └── navigation/          เส้นทางระหว่างหน้า
baselineprofile/             สร้างและวัด baseline profile
docs/                        คู่มือสำหรับตั้งค่าเครื่อง
```

## ปัญหาที่พบบ่อย

| อาการ | วิธีแก้ |
|---|---|
| `SDK location not found` | ตั้ง `ANDROID_HOME` หรือสร้าง `local.properties` |
| Build ล้ม และขึ้นแค่เลขเวอร์ชันอย่าง `25.0.2` | Gradle รันด้วย JDK ที่ไม่รองรับ ให้เปลี่ยนเป็น JDK 17 |
| `Release needs the production API` | ใส่ `-PkioskApiBaseUrl=https://...` ตอน build release |
| ได้ไฟล์ `app-release-unsigned.apk` | ยังไม่มี `keystore.properties` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | เครื่องมีแอปที่ sign คนละกุญแจ ให้รัน `adb uninstall com.pos.kiosk` ก่อน |
| กด Run ใน Android Studio แล้วได้ตัว debug | Run ใช้ variant ที่เลือกในแถบ Build Variants ถ้าจะลงตัว release ให้ใช้คำสั่ง `adb install` ด้านบน |
| เปลี่ยน URL แล้วแอปยังเรียกที่เดิม | URL ฝังตอน build ต้อง build และติดตั้งใหม่ |
