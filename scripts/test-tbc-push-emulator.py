#!/usr/bin/env python3
"""Build a temporary synthetic sender and test Android push delivery on a disposable emulator.
Requires the WHFIN debug and androidTest APKs to be installed first. Never targets a physical phone.
The fixture has the TBC package id solely to exercise the production allowlist. It has no bank UI,
network access, credentials or payment code. An existing TBC installation is never replaced.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

JAVA = 'package com.icomvision.bsc.tbc;\n// Disposable-emulator fixture. This app has no bank UI, credentials, networking or payment code.\npublic class MainActivity extends android.app.Activity {\n public void onCreate(android.os.Bundle state) {\n  super.onCreate(state);\n  android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);\n  manager.createNotificationChannel(new android.app.NotificationChannel("synthetic", "WHFIN synthetic test", 3));\n  String mode = getIntent().getStringExtra("mode");\n  String body = "2.00 GEL\\n(*0001)\\nEXAMPLE BUS 10/09/26 14:07";\n  if ("update".equals(mode)) body += "\\nBalance: 120.00 GEL";\n  if ("loyalty".equals(mode)) body += "\\nBalance: 120.00 GEL\\nYou’ve received: 0.25 GEL\\nIn Ertguli Piggy bank you have: 1.75 GEL";\n  if ("unknown".equals(mode)) body = "Synthetic future format: a new bank template";\n  if ("otp".equals(mode)) body = "TBC SMS code: 0000";\n  manager.notify(getIntent().getIntExtra("id", 1001), new android.app.Notification.Builder(this, "synthetic")\n    .setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle("TBC").setContentText(body)\n    .setStyle(new android.app.Notification.BigTextStyle().bigText(body)).build());\n  finish();\n }\n}\n'
MANIFEST = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.icomvision.bsc.tbc" android:versionCode="1" android:versionName="synthetic-test">\n<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36" />\n<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n<application android:label="WHFIN synthetic push test" android:allowBackup="false">\n<activity android:name=".MainActivity" android:exported="true" />\n</application></manifest>\n'

PACKAGE = "com.icomvision.bsc.tbc"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("Disposable emulator serial required")
sdk = Path(args.sdk)
adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]
def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)
def read(command):
    return subprocess.check_output(command, text=True).strip()
if read(adb + ["shell", "getprop", "ro.hardware"]) not in ("ranchu", "goldfish"):
    raise SystemExit("Disposable emulator hardware required")
if read(adb + ["shell", "pm", "list", "packages", PACKAGE]):
    raise SystemExit("TBC package already present; refusing to replace it")
def version(path):
    return tuple(map(int, re.findall(r"\d+", path.name)))
build_tools = max((sdk / "build-tools").iterdir(), key=version)
platform = max((sdk / "platforms").iterdir(), key=version)
jar = str(platform / "android.jar")
installed = False
with tempfile.TemporaryDirectory(prefix="whfin-synthetic-push-") as temporary:
    root = Path(temporary)
    (root / "classes").mkdir()
    (root / "dex").mkdir()
    (root / "MainActivity.java").write_text(JAVA)
    (root / "AndroidManifest.xml").write_text(MANIFEST)
    apk = str(root / "sender.apk")
    run(["javac", "-source", "8", "-target", "8", "-classpath", jar, "-d", str(root / "classes"), str(root / "MainActivity.java")])
    run([str(build_tools / "d8"), "--lib", jar, "--output", str(root / "dex"), str(root / "classes/com/icomvision/bsc/tbc/MainActivity.class")])
    run([str(build_tools / "aapt2"), "link", "-I", jar, "--manifest", str(root / "AndroidManifest.xml"), "-o", apk])
    with zipfile.ZipFile(apk, "a") as archive:
        archive.write(root / "dex/classes.dex", "classes.dex")
    run(["keytool", "-genkeypair", "-keystore", str(root / "test.jks"), "-storepass", "synthetic-test", "-keypass", "synthetic-test", "-alias", "fixture", "-dname", "CN=WHFIN synthetic test", "-keyalg", "RSA", "-validity", "3"])
    run([str(build_tools / "apksigner"), "sign", "--ks", str(root / "test.jks"), "--ks-pass", "pass:synthetic-test", "--key-pass", "pass:synthetic-test", apk])
    try:
        run(adb + ["install", apk])
        installed = True
        run(adb + ["shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS"])
        result = read(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "externalPush", "true", "-e", "class", "dev.whekin.whfin.data.push.PushListenerDeliveryTest", "dev.whekin.whfin.debug.test/androidx.test.runner.AndroidJUnitRunner"])
        print(result)
        if "OK (1 test)" not in result:
            raise SystemExit("Push delivery test did not pass")
    finally:
        if installed:
            run(adb + ["uninstall", PACKAGE])
