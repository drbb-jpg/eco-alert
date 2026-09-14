"""Exercise the original APK downloaded from the previous successful build."""
import subprocess, pathlib, time, re, json, hashlib, traceback
import xml.etree.ElementTree as ET

OUT = pathlib.Path("test-results")
OUT.mkdir(exist_ok=True)
checks = []
def adb(*args, binary=False):
    return subprocess.check_output(["adb", *args], timeout=40, text=not binary)
def dump():
    for attempt in range(4):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/eco-window.xml")
            return adb("exec-out", "cat", "/sdcard/eco-window.xml")
        except Exception:
            if attempt == 3: raise
            time.sleep(2)
def nodes():
    return list(ET.fromstring(dump()).iter("node"))
def texts():
    return [n.get("text", "") for n in nodes()]
def contains(text):
    assert any(text.casefold() in s.casefold() for s in texts()), "Texte absent : " + text
def tap(text):
    for n in nodes():
        if n.get("text", "").casefold() == text.casefold():
            bounds = [int(x) for x in re.findall(r"\d+", n.get("bounds", ""))]
            assert len(bounds) == 4, "Bornes absentes"
            adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
            time.sleep(1)
            return
    raise AssertionError("Contrôle absent : " + text)
def capture(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    (OUT / (name + ".xml")).write_text(dump(), encoding="utf-8")
def passed(name):
    checks.append({"test": name, "status": "PASS"})
    print("PASS:", name, flush=True)

apks = list(pathlib.Path("original-apk").rglob("*.apk"))
assert len(apks) == 1, "Exactly one original APK expected"
apk = apks[0]
report = {
    "apk": apk.name,
    "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "original_build_commit": "fb4964a0b4c743ad791450da291923227fd0f66f",
    "original_artifact_id": 10357415489,
    "host": "GitHub Actions Ubuntu (not Windows)",
    "android": adb("shell", "getprop", "ro.build.version.release").strip(),
    "api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
    "checks": checks,
    "limitations": [
        "Original GitHub APK tested; uploaded ZIP not independently byte-compared.",
        "Demo data only. Live feed and push notifications not tested.",
        "This is an Android emulator test, not a Windows compatibility test."
    ]
}
failed = None
try:
    result = adb("install", "-r", str(apk))
    assert "Success" in result, result
    passed("Installation de l’APK original")
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", "com.ecoalert.app/.MainActivity")
    time.sleep(3)
    contains("DÉMONSTRATION")
    contains("Précédent")
    contains("Prévu")
    contains("Réel")
    contains("3.2%")
    capture("01-aujourdhui")
    passed("Ouverture et comparatif précédent / prévu / réel")
    tap("Inflation CPI · exemple")
    contains("Source : Démonstration")
    capture("02-detail")
    tap("Fermer")
    passed("Ouverture et fermeture du détail")
    tap("Semaine")
    contains("Cette semaine")
    capture("03-semaine")
    passed("Navigation vers la semaine")
    tap("Tous pays")
    contains("États-Unis")
    tap("États-Unis")
    contains("Zone euro")
    capture("04-filtre-zone-euro")
    tap("Zone euro")
    tap("Majeures")
    contains("Toutes importances")
    passed("Changement des filtres pays et importance")
    tap("Alertes")
    contains("Alertes indisponibles")
    switches = [n for n in nodes() if n.get("class") == "android.widget.Switch"]
    assert len(switches) == 2, "Deux réglages de notifications attendus"
    assert all(n.get("checked") == "false" and n.get("enabled") == "false" for n in switches)
    capture("05-alertes")
    passed("Notifications désactivées et indisponibles en démonstration")
    tap("Délai : 15 minutes")
    tap("5 minutes")
    contains("Délai : 5 minutes")
    passed("Choix du délai de rappel enregistré")
    tap("Fuseau : Africa/Casablanca")
    tap("UTC")
    contains("Fuseau : UTC")
    adb("shell", "am", "force-stop", "com.ecoalert.app")
    adb("shell", "am", "start", "-W", "-n", "com.ecoalert.app/.MainActivity")
    time.sleep(2)
    contains("UTC")
    tap("Alertes")
    contains("Délai : 5 minutes")
    passed("Persistance du fuseau et du délai après relancement")
    tap("Fuseau : UTC")
    tap("Maroc")
    tap("Aujourd’hui")
    contains("Africa/Casablanca")
    capture("06-retour-maroc")
    pid = adb("shell", "pidof", "com.ecoalert.app").strip()
    assert pid, "Application arrêtée"
    logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    (OUT / "android-runtime.log").write_text(logs, encoding="utf-8")
    assert "FATAL EXCEPTION" not in logs, logs
    passed("Aucun crash AndroidRuntime pendant le parcours")
except Exception as exc:
    failed = exc
    checks.append({"test": str(exc), "status": "FAIL"})
    (OUT / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    try: capture("99-echec")
    except Exception: pass
finally:
    report["result"] = "FAIL" if failed else "PASS"
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# Test réel de l’APK Éco Alert", "", "Résultat : **" + report["result"] + "**",
        "", "Android " + report["android"] + " (API " + report["api"] + "), émulateur sur Ubuntu.",
        "APK original de la compilation fb4964a0b4c743ad791450da291923227fd0f66f.",
        "", "| Vérification | Résultat |", "|---|---|"]
    lines += ["| " + c["test"] + " | " + c["status"] + " |" for c in checks]
    lines += ["", "Limites : données fictives ; flux réel et notifications non testés ; aucun test Windows ; ZIP joint non comparé octet par octet.", "", "## Captures"]
    for picture in sorted(OUT.glob("*.png")):
        lines += ["", "### " + picture.stem, "", "![" + picture.stem + "](" + picture.name + ")"]
    (OUT / "README.md").write_text("\n".join(lines), encoding="utf-8")
if failed: raise failed
