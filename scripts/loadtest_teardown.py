#!/usr/bin/env python3
"""
Clean up everything created by loadtest_med_reminder.py.

Deletes in FK-safe order:
  1. medication_reminder_events for LoadTest timetables
  2. medication_timetables (LoadTest-Med*)
  3. consultations belonging to LoadGP doctors
  4. royal_checkin_* rows for LoadGP doctors (best effort)
  5. doctor_earnings for LoadGP/LoadNurse doctors
  6. fcm_delivery_failures for those user_ids
  7. notifications for those user_ids / sessions
  8. doctor_profiles (LoadGP*, LoadNurse*)
  9. patient_sessions (patient_id starts with 'LOAD-')
 10. auth.users for the deleted profiles + sessions

Run:  python3 scripts/loadtest_teardown.py
"""

import json
import sys
import urllib.parse
import urllib.request
import urllib.error

SUPABASE_URL = "https://nzzvphhqbcscoetzfzkd.supabase.co"


def _read_env(key: str) -> str:
    with open(".env.local", "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip()
    raise RuntimeError(f"{key} not in .env.local")


KEY = _read_env("SUPABASE_SERVICE_ROLE_KEY")


def _request(method: str, path: str, body=None) -> str:
    headers = {
        "apikey": KEY,
        "Authorization": f"Bearer {KEY}",
        "Content-Type": "application/json",
    }
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(SUPABASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode()
    except urllib.error.HTTPError as e:
        return f"ERR {e.code}: {e.read().decode()[:200]}"


def get(path: str) -> list:
    txt = _request("GET", path)
    if txt.startswith("ERR"):
        print("  ", txt)
        return []
    return json.loads(txt)


def delete(path: str) -> str:
    return _request("DELETE", path)


def main() -> int:
    # 1. Find LoadGP / LoadNurse doctor_ids (URL-encode the spaces in the
    #    full_name pattern — bare spaces choke urllib's path validator).
    print("Locating LoadGP / LoadNurse profiles…")
    gps = get("/rest/v1/doctor_profiles?full_name=ilike.Dr%20LoadGP*&select=doctor_id")
    nurses = get("/rest/v1/doctor_profiles?full_name=ilike.Dr%20LoadNurse*&select=doctor_id")
    gp_ids = [r["doctor_id"] for r in gps]
    nurse_ids = [r["doctor_id"] for r in nurses]
    all_doctor_ids = gp_ids + nurse_ids
    print(f"  GPs={len(gp_ids)}  Nurses={len(nurse_ids)}")

    # 2. Find LoadTest timetables
    print("Locating LoadTest timetables…")
    tt = get("/rest/v1/medication_timetables?medication_name=ilike.LoadTest-Med*&select=timetable_id,patient_session_id")
    tt_ids = [r["timetable_id"] for r in tt]
    session_ids_from_tt = list({r["patient_session_id"] for r in tt})
    print(f"  timetables={len(tt_ids)}")

    # 3. Find LoadTest patient_sessions (patient_id LOAD-*)
    print("Locating LoadTest patient_sessions…")
    sessions = get("/rest/v1/patient_sessions?patient_id=like.LOAD-*&select=session_id")
    load_session_ids = list({r["session_id"] for r in sessions} | set(session_ids_from_tt))
    print(f"  sessions={len(load_session_ids)}")

    # ── deletes (FK-safe order) ────────────────────────────────────────
    if tt_ids:
        in_clause = "(" + ",".join(tt_ids) + ")"
        print(f"Deleting medication_reminder_events for {len(tt_ids)} timetables…")
        print(" ", delete(f"/rest/v1/medication_reminder_events?timetable_id=in.{in_clause}")[:80])
        print(f"Deleting medication_timetables…")
        print(" ", delete(f"/rest/v1/medication_timetables?timetable_id=in.{in_clause}")[:80])

    if gp_ids:
        in_clause = "(" + ",".join(gp_ids) + ")"
        print("Deleting royal_checkin_escalation_calls (cascade prep)…")
        # Cascade via FK from escalations; just delete escalations directly
        print("Deleting royal_checkin_escalations for LoadGPs…")
        print(" ", delete(f"/rest/v1/royal_checkin_escalations?doctor_id=in.{in_clause}")[:80])
        print("Deleting royal_checkin_reminders for LoadGPs…")
        print(" ", delete(f"/rest/v1/royal_checkin_reminders?doctor_id=in.{in_clause}")[:80])
        print("Deleting consultations for LoadGPs…")
        print(" ", delete(f"/rest/v1/consultations?doctor_id=in.{in_clause}")[:80])

    if all_doctor_ids:
        in_clause = "(" + ",".join(all_doctor_ids) + ")"
        print("Deleting doctor_earnings for LoadGP/LoadNurse…")
        print(" ", delete(f"/rest/v1/doctor_earnings?doctor_id=in.{in_clause}")[:80])
        print("Deleting fcm_delivery_failures…")
        print(" ", delete(f"/rest/v1/fcm_delivery_failures?user_id=in.{in_clause}")[:80])
        print("Deleting fcm_tokens…")
        print(" ", delete(f"/rest/v1/fcm_tokens?user_id=in.{in_clause}")[:80])

    user_id_set = set(all_doctor_ids) | set(load_session_ids)
    if user_id_set:
        in_clause = "(" + ",".join(user_id_set) + ")"
        print(f"Deleting notifications for {len(user_id_set)} user_ids/sessions…")
        print(" ", delete(f"/rest/v1/notifications?user_id=in.{in_clause}")[:80])

    if all_doctor_ids:
        in_clause = "(" + ",".join(all_doctor_ids) + ")"
        print("Deleting doctor_profiles for LoadGP/LoadNurse…")
        print(" ", delete(f"/rest/v1/doctor_profiles?doctor_id=in.{in_clause}")[:80])

    if load_session_ids:
        in_clause = "(" + ",".join(load_session_ids) + ")"
        print("Deleting patient_sessions (LOAD-*)…")
        print(" ", delete(f"/rest/v1/patient_sessions?session_id=in.{in_clause}")[:80])

    # 4. Delete auth.users for both doctors and patient sessions
    print(f"Deleting {len(all_doctor_ids) + len(load_session_ids)} auth.users…")
    deleted = 0
    failed = 0
    for uid in all_doctor_ids + load_session_ids:
        r = delete(f"/auth/v1/admin/users/{uid}")
        if r.startswith("ERR"):
            failed += 1
        else:
            deleted += 1
    print(f"  auth users deleted={deleted} failed={failed}")

    print("\nTeardown complete.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
