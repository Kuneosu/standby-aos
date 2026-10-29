#!/usr/bin/env python3
"""지정한 Android 기기의 앱 진단 기록만 읽어 로컬 폴더에 저장한다."""

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True, help="adb devices에 표시되는 기기 식별자")
parser.add_argument("--adb", default=shutil.which("adb") or str(Path(os.environ.get("ANDROID_HOME", "~/Library/Android/sdk")).expanduser() / "platform-tools/adb"))
parser.add_argument("--output", type=Path, default=Path("output/device"))
args = parser.parse_args()


def adb(*command):
    return subprocess.run([args.adb, "-s", args.serial, *command], check=True, capture_output=True, text=True).stdout.strip()


if adb("get-state") != "device":
    raise SystemExit("연결되고 인증된 기기를 찾지 못했습니다.")

metadata = {"collectedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "package": "dev.kwonsu.standby"}
for field, prop in {
    "model": "ro.product.model",
    "androidVersion": "ro.build.version.release",
    "sdk": "ro.build.version.sdk",
    "oneUiVersion": "ro.build.version.oneui",
}.items():
    metadata[field] = adb("shell", "getprop", prop)

journal = adb("shell", "run-as", "dev.kwonsu.standby", "cat", "files/diagnostics.jsonl")
events = [json.loads(line) for line in journal.splitlines() if line.strip()]
metadata["eventCount"] = len(events)
args.output.mkdir(parents=True, exist_ok=True)
(args.output / "diagnostics.jsonl").write_text(journal + "\n", encoding="utf-8")
(args.output / "device.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata, ensure_ascii=False, indent=2))
for event in events[-12:]:
    print(event["time"], event["event"], event["details"])
