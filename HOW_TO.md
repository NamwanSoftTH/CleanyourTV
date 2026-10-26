**[← README](README.md)** · **[Showcase](README.md#showcase)** · **[ติดตั้งกับเครื่องใหม่](#ติดตั้งกับเครื่องใหม่)** ·
**[Mac / Linux](#build-บน-mac--linux)** · **[TV รุ่นอื่น (OTHER_DEVICE)](OTHER_DEVICE.md)**

## Install

ถ้าภายหลังอยากแก้อะไร ให้เปิด Wireless debugging แล้วส่ง IP:พอร์ตใหม่มา หรือแก้ config.xml เองแล้วรัน `.\install-launcher.ps1` ตามที่เขียนไว้ใน README.md

### Build บน Mac / Linux

ใช้ `install-launcher.sh` แทน `install-launcher.ps1` (ทำงานเหมือนกัน):

```sh
adb connect 192.168.x.x:<พอร์ต>
TV_SERIAL=192.168.x.x:<พอร์ต> ./install-launcher.sh --set-home   # ครั้งแรก: ติดตั้ง + ตั้งเป็น Home
TV_SERIAL=192.168.x.x:<พอร์ต> ./install-launcher.sh              # ครั้งต่อไป: ติดตั้งทับ
./install-launcher.sh --build-only                                # build อย่างเดียว
```

ต้องมี: `adb` ใน PATH (Android SDK platform-tools), Android SDK (หาเองที่ `~/Library/Android/sdk` บน Mac
หรือ `~/Android/Sdk` บน Linux หรือตั้ง `ANDROID_HOME`) และ JDK 17+ (หา JDK ของ Android Studio ให้เอง
หรือตั้ง `JAVA_HOME`) ครั้งแรก Gradle wrapper จะดาวน์โหลด Gradle 9.5.0 ให้อัตโนมัติ
ส่วน `setup-new-box.ps1`, `restore.ps1` และ `tools\*.ps1` ใช้ได้บน Windows เท่านั้น
(หรือ PowerShell 7 บน Mac/Linux โดยต้องแก้ path ของ adb ในสคริปต์)

### Wallpaper

ถ้าจะเปลี่ยนพื้นหลังครั้งหน้า ใช้สคริปต์ที่ผมเพิ่มไว้ (อยู่ใน README แล้ว):
`python -I tools\fit-wallpaper.py <ไฟล์ใหญ่.jpg> launcher\app\src\main\res\drawable-nodpi\<ชื่อ>.jpg`

### ติดตั้งกับเครื่องใหม่

รันทุกคำสั่งใน PowerShell ที่โฟลเดอร์ `D:\_Claude\tvbox-s3gen` ทุกสคริปต์อ่านเครื่องเป้าหมายจาก `$env:TV_SERIAL`

#### ทางเลือก: สคริปต์อัตโนมัติ `setup-new-box.ps1`

ทำ **ข้อ 1** (เตรียมทีวีและจับคู่ ADB) ด้วยมือก่อน จากนั้นสคริปต์จะทำข้อ 2–6 ให้ทั้งหมด:

```powershell
.\setup-new-box.ps1 -Serial 192.168.x.x:<พอร์ต>
```

สิ่งที่สคริปต์ทำ ตามลำดับ:

1. เช็กการเชื่อมต่อ, รุ่น และ Android (ต้อง SDK 26+) ถ้ายังไม่มี `debloat/<รุ่น>.txt` ของรุ่นนี้ จะ**ข้าม debloat** และถามก่อนทำต่อ
2. สำรองข้อมูลลงโฟลเดอร์ `backup\<รุ่น>-<เลขท้าย serial 5 ตัว>-<วันเวลา>\`
3. ปิดแพ็กเกจตาม `debloat/<รุ่น>.txt` ของรุ่นที่ต่ออยู่ ทีละ batch (ถ้าไม่มีไฟล์ของรุ่นนั้นจะข้าม debloat; ข้ามแพ็กเกจที่ไม่มีในเครื่อง; ไม่แตะ Backdrop)
4. build และติดตั้งลอนเชอร์ แล้วตั้งเป็น Home
5. ถามทีละข้อ: เปิดตำแหน่ง (ชื่อ Wi-Fi), เปิดปุ่มช่วยรีโมต และให้สิทธิ์ `WRITE_SECURE_SETTINGS` (ค่าเริ่มต้นคือไม่ให้)
6. เช็กว่า Wi-Fi เป็นของ setupwraith หรือไม่ ถ้าใช่ **หยุดก่อน** และบอกวิธีแก้ ถ้าไม่ใช่ ปิด `launcherx` + `setupwraith`
   แล้วกด Home เพื่อตรวจ ถ้า Home ไม่ใช่ลอนเชอร์ใหม่ จะเปิด `launcherx` กลับให้เองอัตโนมัติ
7. สรุปผล และบอกขั้นต่อไป (รีสตาร์ท, ลองรีโมตจริง, ปิด ADB)

ตัวเลือกเพิ่มเติม:

| ตัวเลือก                                 | ใช้เมื่อ                                                                |
| ---------------------------------------- | ----------------------------------------------------------------------- |
| `-PrefsFile backup\s3gen-home-prefs.xml` | คัดลอกลำดับแอปที่เรียงไว้บนเครื่องอื่น                                  |
| `-SkipDebloat`                           | ติดตั้งลอนเชอร์อย่างเดียว ไม่ปิดแพ็กเกจ                                 |
| `-Yes`                                   | ไม่ต้องถาม: เปิดตำแหน่งและปุ่มช่วยรีโมต, ไม่ให้ `WRITE_SECURE_SETTINGS` |
| `-BackupDir <ชื่อ>`                      | ตั้งชื่อโฟลเดอร์สำรองเอง                                                |

รันซ้ำได้ปลอดภัย: ขั้นที่ทำไปแล้วจะทำซ้ำโดยไม่มีผลเสีย เช่น หลังแก้ Wi-Fi ของ setupwraith ให้เชื่อมต่อ ADB ใหม่ (พอร์ตใหม่) แล้วรันอีกครั้ง

ถ้าอยากทำเองทีละขั้น หรือสคริปต์หยุดกลางทาง ใช้ขั้นตอนด้านล่าง:

### 1. เตรียมทีวี

1. Settings → Device Preferences → About → กด OK ที่ **Build** 7 ครั้ง
2. Developer options → เปิด **USB debugging** และ **Wireless debugging**
3. ครั้งแรกต้องจับคู่: Wireless debugging → **จับคู่อุปกรณ์ด้วยรหัสการจับคู่** แล้วรันบนคอม
   (พอร์ตจับคู่กับพอร์ตเชื่อมต่อเป็นคนละพอร์ต ดูทั้งสองค่าจากหน้าจอทีวี)

```powershell
adb pair 192.168.x.x:<พอร์ตจับคู่> <รหัส 6 หลัก>
adb connect 192.168.x.x:<พอร์ต Wireless debugging>
$env:TV_SERIAL = '192.168.x.x:<พอร์ต Wireless debugging>'
adb -s $env:TV_SERIAL shell "getprop ro.product.model; getprop ro.build.version.sdk"
```

ลอนเชอร์ต้องการ Android 8.0 ขึ้นไป (SDK 26+) พอร์ต Wireless debugging เปลี่ยนทุกครั้งที่รีสตาร์ทหรือ Wi-Fi หลุด

### 2. สำรองข้อมูลก่อนแก้

ใช้โฟลเดอร์ย่อยใน `backup\` แยกต่อเครื่อง เช่น `backup\box2\` (ทั้ง `backup\` ไม่ขึ้น git เพราะมีข้อมูลส่วนตัวของเครื่อง)

```powershell
$b = 'backup\box2'; New-Item -ItemType Directory -Force $b | Out-Null
adb -s $env:TV_SERIAL shell pm list packages -f   > "$b\packages-all.txt"
adb -s $env:TV_SERIAL shell pm list packages -d   > "$b\packages-disabled.txt"
adb -s $env:TV_SERIAL shell getprop               > "$b\getprop.txt"
foreach ($n in 'global','secure','system') { adb -s $env:TV_SERIAL shell settings list $n > "$b\settings-$n.txt" }
```

### 3. Debloat

- **รุ่นเดียวกัน (Xiaomi TV Box S 3rd Gen, MiTV-AFMU0):** ปิดตามรายการใน `debloat/MiTV-AFMU0.txt`
  ทีละกลุ่ม **ยกเว้น 2 ตัวสุดท้าย** (`launcherx`, `setupwraith`) ซึ่งต้องปิดหลังติดตั้งลอนเชอร์ (ข้อ 5)

  ```powershell
  .\tools\disable.ps1 com.miui.tv.analytics com.xiaomi.statistic com.android.tv.feedbackconsent com.google.android.feedback
  .\tools\disable.ps1 com.mitv.tvhome.michannel com.mitv.tvhome.oemtab com.xm.webcontent com.xiaomi.android.tvsetup.partnercustomizer
  .\tools\disable.ps1 com.xiaomi.mimusic2 com.google.android.play.games
  ```

  `disable.ps1` ปิดทีละแพ็กเกจ ไม่ยอมปิดแพ็กเกจหลักของ Android และเตือนถ้าตัวไหนปิดไม่สำเร็จ
  **อย่าปิด** `com.google.android.apps.tv.dreamx` (Backdrop) เพราะเป็นหน้าจอพักเครื่องตัวเดียวของกล่อง

- **รุ่นอื่น:** ชื่อแพ็กเกจต่างกัน อย่าใช้รายการเดิมตรง ๆ ตรวจแต่ละตัวด้วย
  `adb shell dumpsys package <ชื่อ>` ก่อน (ห้ามเปิดแอปที่ชื่อมี factory / fac menu)

### 4. ติดตั้งลอนเชอร์

```powershell
.\install-launcher.ps1 -SetHome
adb -s $env:TV_SERIAL shell cmd location set-location-enabled true
```

`install-launcher.ps1` build, ติดตั้ง และให้สิทธิ์ตำแหน่ง (ชื่อ Wi-Fi) กับ Nearby devices (รีโมตและอุปกรณ์เสริม)
ส่วนนี้ต้อง **รันเอง** (สิทธิ์ที่ให้แอปแก้การตั้งค่าระบบได้ ใช้เปิดหน้ารายการอุปกรณ์เท่านั้น ไม่ให้ก็ได้
เมนูจะเปิดหน้าตั้งค่าหลักแทน):

```powershell
adb -s $env:TV_SERIAL shell pm grant com.launcher.clean android.permission.WRITE_SECURE_SETTINGS
```

ปุ่มช่วยรีโมต (ปุ่ม YouTube, กด Home ค้าง = ตั้งค่าด่วน) **แลกกับการกดค้างไม่ทำงาน**
(เสียงขึ้นทีละขั้น กดลูกศรค้างไม่เลื่อนต่อเนื่อง):

```powershell
adb -s $env:TV_SERIAL shell settings put secure enabled_accessibility_services com.launcher.clean/com.launcher.clean.KeyService
adb -s $env:TV_SERIAL shell settings put secure accessibility_enabled 1
```

### 5. ยึดหน้า Home ⚠️ ขั้นที่ต้องระวังที่สุด

ตรวจก่อนว่า Wi-Fi เป็นของใคร:

```powershell
adb -s $env:TV_SERIAL shell "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u"
```

- ขึ้น `android.uid.system` → ทำต่อได้
- ขึ้น `setupwraith` → Wi-Fi ถูกบันทึกตอนตั้งค่าเครื่องครั้งแรก และ **จะถูกลบทันทีที่ปิด setupwraith**
  (ทีวีหลุด Wi-Fi และหลุด ADB) ให้ลืมเครือข่ายแล้วเชื่อมต่อใหม่ใน Settings ก่อน จนตรวจแล้วขึ้น `android.uid.system`

แล้วปิดหน้า Home เดิมกับ setup wizard และตรวจว่า Home เป็นลอนเชอร์ใหม่:

```powershell
.\tools\disable.ps1 com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith
adb -s $env:TV_SERIAL shell cmd package set-home-activity com.launcher.clean/.HomeActivity
adb -s $env:TV_SERIAL shell input keyevent KEYCODE_HOME
adb -s $env:TV_SERIAL shell "dumpsys activity activities | grep mFocusedApp"
```

ต้องเห็น `com.launcher.clean/.HomeActivity` ถ้าจอดำ ให้เปิด `launcherx` กลับ:
`adb shell pm enable --user 0 com.google.android.apps.tv.launcherx`

### 6. สิ่งที่ไม่ตามไปเอง

- **ลำดับแอปที่เรียงบนทีวี** เก็บแยกในแต่ละเครื่อง ถ้าอยากให้เหมือนเครื่องเดิม ใส่ใน `dock_apps` / `app_order`
  ใน `config.xml` ก่อน build หรือคัดลอกไฟล์ที่สำรองไว้ (ทำตอนลอนเชอร์ไม่ได้ทำงาน):

  ```powershell
  $n = 'com.launcher.clean'
  adb -s $env:TV_SERIAL shell am force-stop $n
  adb -s $env:TV_SERIAL push backup\s3gen-home-prefs.xml /data/local/tmp/home.xml
  adb -s $env:TV_SERIAL shell "run-as $n mkdir -p shared_prefs && run-as $n cp /data/local/tmp/home.xml shared_prefs/home.xml && rm /data/local/tmp/home.xml"
  # force-stop ปิดปุ่มช่วยรีโมต (accessibility) ไปด้วย ต้องเปิดกลับ:
  adb -s $env:TV_SERIAL shell settings put secure enabled_accessibility_services "$n/$n.KeyService"
  adb -s $env:TV_SERIAL shell settings put secure accessibility_enabled 1
  ```

  ⚠️ ทุกครั้งที่ `am force-stop` ลอนเชอร์ Android จะปิดปุ่มช่วยรีโมตให้เอง (ปุ่ม YouTube / กด Home ค้างใช้ไม่ได้)
  ให้รัน 2 บรรทัดสุดท้ายข้างบนอีกครั้ง

- แอปใน dock / ลำดับที่ไม่มีในเครื่องใหม่ จะถูกข้ามไปเอง
- จอขนาดอื่น: layout ปรับตามขนาดจออัตโนมัติ

### 7. ทดสอบ แล้วปิด ADB

1. รีสตาร์ทกล่องหนึ่งครั้ง แล้วตรวจว่า Home ยังเป็นลอนเชอร์ใหม่ และแพ็กเกจที่ปิดไม่กลับมา (`adb shell pm list packages -d`)
2. ลองด้วยรีโมตจริง: Home, ปุ่ม YouTube, กด Home ค้าง (ตั้งค่าด่วน), กด OK ค้างที่แอป (ย้าย)
3. Developer options → ปิด **Wireless debugging** และ **USB debugging** → **เพิกถอนการให้สิทธิ์ USB debugging** → รีสตาร์ท
   (ตอนเปิด debugging อยู่ พอร์ต USB ของกล่องใช้กับแฟลชไดรฟ์ไม่ได้)

คืนค่าทั้งหมด: `.\restore.ps1` แล้ว `adb reboot`
