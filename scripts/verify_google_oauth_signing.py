#!/usr/bin/env python3
"""Fail closed when the APK signing SHA-1 is not registered for Google OAuth.

The Android package name, APK certificate and the Firebase google-services.json
Android OAuth client must match. Merely including a web OAuth client ID does
NOT prove that a Google account can sign in.

No OAuth client IDs, API keys, tokens or configuration file contents are logged.
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


def compact_sha1(value: str) -> str:
    cleaned = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if len(cleaned) != 40:
        raise ValueError("SHA-1 değeri 40 onaltılık karakter olmalı.")
    return cleaned


def registered_signers(config: dict, package: str) -> set[str]:
    matches = set()
    for client in config.get("client", []):
        info = client.get("client_info", {}).get("android_client_info", {})
        if info.get("package_name") != package:
            continue
        for oauth in client.get("oauth_client", []):
            if oauth.get("client_type") != 1:
                continue
            android = oauth.get("android_info") or {}
            if android.get("package_name") != package:
                continue
            try:
                matches.add(compact_sha1(android["certificate_hash"]))
            except (KeyError, ValueError, TypeError):
                pass
    return matches


def signer_sha1(apksigner: str, apk: Path) -> str:
    result = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk)],
        text=True, capture_output=True, check=True
    )
    match = re.search(
        r"Signer #1 certificate SHA-1 digest:\s*([A-Fa-f0-9:]+)",
        result.stdout
    )
    if not match:
        raise ValueError("APK imza sertifikası okunamadı.")
    return compact_sha1(match.group(1))


def run() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--firebase", type=Path, required=True)
    p.add_argument("--package", default="com.denemelab.preview")
    p.add_argument("--apksigner", default=os.environ.get("APK_SIGNER", "apksigner"))
    args = p.parse_args()

    config = json.loads(args.firebase.read_text(encoding="utf-8"))
    signer = signer_sha1(args.apksigner, args.apk)
    allowed = registered_signers(config, args.package)

    # SHA-1 fingerprint is public identity, not a credential.
    formatted = ":".join(signer[i:i + 2] for i in range(0, 40, 2)).upper()
    print("DenemeLab APK sertifika SHA-1:", formatted)
    if not allowed:
        print("::error::Firebase Android OAuth istemcisi bulunamadı. "
              "Firebase Console'da Android SHA-1 eklenip "
              "google-services.json ve GitHub Secret yenilenmeli.")
        return 1
    if signer not in allowed:
        print("::error::APK imzası Firebase google-services.json içindeki "
              "Android OAuth sertifikalarıyla eşleşmiyor; Google girişinde "
              "DEVELOPER_ERROR(10) oluşur. APK yayınlanmıyor.")
        return 1
    web_clients = [
        oauth for client in config.get("client", [])
        if client.get("client_info", {}).get("android_client_info", {})
            .get("package_name") == args.package
        for oauth in client.get("oauth_client", [])
        if oauth.get("client_type") == 3
    ]
    if not web_clients:
        print("::error::Google ID token için web OAuth istemci kimliği eksik.")
        return 1

    print("APK imzası + Firebase Android OAuth sertifikası + web istemcisi eşleşti.")
    print("Not: Bu statik kontrol gerçek Google hesap girişini tek başına kanıtlamaz.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(run())
    except (OSError, ValueError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print("::error::Firebase OAuth imza kontrolü çalışmadı:", str(exc))
        sys.exit(1)
