#!/usr/bin/env python3
"""Contract probe for the publicly reachable YÖK Atlas JSON APIs.

Runs only through an explicitly triggered dev CI workflow. No personal data,
no scraping of old HTML pages, and no mass data downloads.
"""
import json
import sys
import urllib.request

BASE = "https://yokatlas.yok.gov.tr"
HEADERS = {
    "Accept": "application/json",
    "User-Agent": "DenemeLab/0.3 public-contract-probe",
}

def fetch(path, payload=None):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={**HEADERS, **({"Content-Type": "application/json"} if data is not None else {})},
        method="POST" if data is not None else "GET",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        if "json" not in resp.headers.get("Content-Type", "").lower():
            raise RuntimeError(f"Non-JSON response from {path}")
        return json.loads(resp.read(2_000_000).decode("utf-8"))

def filters():
    return {
        "puanTuru": "SAY", "universiteId": [], "birimGrupId": [], "ilKodu": [],
        "birimTuruId": None, "universiteTuru": None, "bursOraniId": None,
        "ogrenimTuruId": None, "kilavuzKodu": None,
        "minBasariSirasi": None, "maxBasariSirasi": None,
    }

def main():
    universities = fetch("/api/tercih-kilavuz/universiteler")
    groups = fetch("/api/tercih-kilavuz/universite-programlar")
    if not isinstance(universities, list) or len(universities) < 100:
        raise AssertionError("University catalog not available")
    if not isinstance(groups, list) or len(groups) < 100:
        raise AssertionError("Program group catalog not available")
    if not {"universiteId", "universiteAdi"}.issubset(universities[0]):
        raise AssertionError("University lookup contract changed")
    if not {"birimGrupId", "birimGrupAdi"}.issubset(groups[0]):
        raise AssertionError("Program lookup contract changed")
    print("University and program group lookups valid", len(universities), len(groups))

    page = fetch("/api/tercih-kilavuz/search", {
        "filters": filters(), "page": 0, "size": 5,
        "sortBy": "basariSirasi", "direction": "ASC",
    })
    items = page.get("content")
    if not isinstance(items, list) or not items:
        raise AssertionError("Program search returned no rows")
    required = {"kilavuzKodu", "universiteAdi", "birimAdi", "puanTuru", "universiteTuru"}
    if not required.issubset(items[0]):
        raise AssertionError("Program search fields changed")
    first = items[0]
    print("Search contract valid", "year", page.get("yil"), "total", page.get("totalElements"))
    f = {
        "puanTuru": first.get("puanTuru"),
        "universiteId": first.get("universiteId"),
        "birimGrupId": first.get("birimGrupId"),
        "birimTuruId": None, "universiteTuru": None,
        "yil": None, "katsayi": None,
    }
    if f["universiteId"] and f["birimGrupId"]:
        nets = fetch("/api/netler/search", {"filters": f, "page": 0, "size": 20})
        if not isinstance(nets.get("content"), list):
            raise AssertionError("Netler search contract changed")
        print("Net search contract valid", "returned", len(nets["content"]),
              "years", sorted({row.get("yil") for row in nets["content"] if row.get("yil")}))
    print("PASS: public JSON endpoints available")

if __name__ == "__main__":
    try:
        main()
    except Exception as ex:
        print(f"API contract unavailable or changed: {type(ex).__name__}: {ex}", file=sys.stderr)
        sys.exit(1)
