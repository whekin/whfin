#!/usr/bin/env python3
"""Drive the production TBC route using emulator SMS and a synthetic bank transport."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
args = parser.parse_args()
if not re.fullmatch(r"emulator-[0-9]+", args.serial):
    parser.error("Use an explicit disposable emulator serial")
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")
adb = [shutil.which("adb") or str(sdk / "platform-tools/adb"), "-s", args.serial]
hardware = subprocess.check_output(adb + ["shell", "getprop", "ro.hardware"], text=True).strip()
if hardware not in {"ranchu", "goldfish"}:
    raise SystemExit("Refusing to run on a data-bearing phone")
subprocess.run(adb + ["shell", "pm", "grant", "dev.whekin.whfin.debug", "android.permission.RECEIVE_SMS"], check=True)
log = Path(tempfile.mkdtemp(prefix="whfin-tbc-otp-")) / "result.log"
with log.open("w") as output:
    process = subprocess.Popen(adb + ["shell", "am", "instrument", "-w", "-e", "externalSms", "true", "-e", "class",
        "dev.whekin.whfin.ui.settings.TbcOtpDeliveryTest",
        "dev.whekin.whfin.debug.test/androidx.test.runner.AndroidJUnitRunner"], stdout=output, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline and process.poll() is None:
        if "TBC_OTP_QA_READY" in log.read_text():
            break
        time.sleep(.25)
    if "TBC_OTP_QA_READY" in log.read_text():
        # Observed wrapper, with synthetic code/hash; the hash deliberately also contains digits.
        message = "<#> TBC SMS code: 246810 Please, make sure you are entering it on https://tbconline.ge or in TBC mobilebank Abcd123456E"
        subprocess.run(adb + ["emu", "sms", "send", "TBCSMS", message], check=True)
    try:
        process.wait(timeout=40)
    except subprocess.TimeoutExpired:
        process.kill()
        subprocess.run(adb + ["shell", "am", "force-stop", "dev.whekin.whfin.debug.test"], check=True)
result = log.read_text()
print(result)
print(f"Log: {log}")
raise SystemExit(0 if "OK (1 test)" in result and "FAILURES!!!" not in result else 1)
