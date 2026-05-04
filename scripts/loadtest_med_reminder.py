#!/usr/bin/env python3
"""
Load-test fixture for the medication-reminder cron.

Creates N GP doctors, N nurses, N patient sessions, N Royal consultations,
N medication timetables — all wired so a single cron tick should fan out
N parallel rings via the new concurrency=50 path.

Run:
  python3 scripts/loadtest_med_reminder.py [N]   (default N=10)

After it finishes, the cron's *next* HH:MM tick (~1-2 minutes from now)
will fire all N timetables at once. The script prints the fire window so
you can watch in the dashboard.
"""

import datetime
import json
import os
import sys
import time
import urllib.request
import urllib.error
import uuid

SUPABASE_URL = "https://nzzvphhqbcscoetzfzkd.supabase.co"
CRON_SECRET = "a375791bc55596511dfb7229b3aafb3a6011443023ec01de"


def _read_env(key: str) -> str:
    with open(".env.local", "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip()
    raise RuntimeError(f"{key} not in .env.local")


KEY = _read_env("SUPABASE_SERVICE_ROLE_KEY")


def _post(path: str, body: dict, *, prefer_repr=False, auth_admin=False) -> dict:
    headers = {
        "apikey": KEY,
        "Authorization": f"Bearer {KEY}",
        "Content-Type": "application/json",
    }
    if prefer_repr:
        headers["Prefer"] = "return=representation"
    req = urllib.request.Request(
        SUPABASE_URL + path,
        data=json.dumps(body).encode(),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            txt = r.read().decode()
            return json.loads(txt) if txt else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        raise RuntimeError(f"{e.code} {path}: {body[:300]}") from None


def create_auth_user(email: str, role: str) -> str:
    res = _post(
        "/auth/v1/admin/users",
        {
            "email": email,
            "password": "TestPass123!Load",
            "email_confirm": True,
            "user_metadata": {"role": role},
        },
    )
    return res["id"]


def create_doctor_profile(doctor_id: str, name: str, specialty: str, idx: int) -> None:
    _post(
        "/rest/v1/doctor_profiles",
        {
            "doctor_id": doctor_id,
            "full_name": name,
            "email": f"loadtest_{specialty}_{idx}_{int(time.time())}@test.local",
            "phone": f"+255{700000000 + idx}",
            "specialty": specialty,
            "license_number": f"LOAD-{specialty.upper()}-{idx}-{uuid.uuid4().hex[:6]}",
            "is_verified": True,
            "is_available": True,
            "is_banned": False,
            "in_session": False,
        },
    )


def create_patient_session(idx: int) -> str:
    sid = str(uuid.uuid4())
    nonce = uuid.uuid4().hex
    # Synthetic auth columns — bcrypt fields are hash-only validators, the
    # cron never reads them, so anything that satisfies NOT NULL works.
    _post(
        "/rest/v1/patient_sessions",
        {
            "session_id": sid,
            "session_token_hash": f"loadtest_token_hash_{idx}_{nonce}",
            "session_token_bcrypt": f"loadtest_bcrypt:{nonce}",
            "refresh_token_hash": f"loadtest_refresh_hash_{idx}_{nonce}",
            "refresh_token_bcrypt": f"loadtest_refresh_bcrypt:{nonce}",
            "patient_id": f"LOAD-{idx:04d}-{nonce[:4]}",
            "patient_id_hash": f"loadtest_pid_hash_{idx}_{nonce}",
            "fcm_token": f"loadtest_fcm_{idx}_{nonce}",
            "is_active": True,
        },
    )
    return sid


def create_consultation(doctor_id: str, patient_session_id: str, idx: int) -> str:
    cid = str(uuid.uuid4())
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    expiry = (now_utc + datetime.timedelta(days=14)).isoformat(timespec="seconds")
    _post(
        "/rest/v1/consultations",
        {
            "consultation_id": cid,
            "doctor_id": doctor_id,
            "patient_session_id": patient_session_id,
            "service_tier": "ROYAL",
            "service_type": "gp",
            "consultation_type": "chat",
            "status": "completed",
            "consultation_fee": 420000,
            "request_expires_at": expiry,
            "session_start_time": (now_utc - datetime.timedelta(hours=1)).isoformat(timespec="seconds"),
            "session_end_time": now_utc.isoformat(timespec="seconds"),
            "session_duration_minutes": 15,
            "follow_up_expiry": expiry,
        },
    )
    return cid


def create_timetable(
    consultation_id: str, doctor_id: str, patient_session_id: str, fire_hhmm: str, idx: int
) -> str:
    tid = str(uuid.uuid4())
    today = datetime.date.today()
    _post(
        "/rest/v1/medication_timetables",
        {
            "timetable_id": tid,
            "consultation_id": consultation_id,
            "doctor_id": doctor_id,
            "patient_session_id": patient_session_id,
            "medication_name": f"LoadTest-Med-{idx}",
            "form": "Tablets",
            "times_per_day": 1,
            "scheduled_times": [fire_hhmm],
            "duration_days": 1,
            "start_date": today.isoformat(),
            "end_date": (today + datetime.timedelta(days=1)).isoformat(),
            "is_active": True,
        },
    )
    return tid


def get_eat_minute() -> str:
    headers = {
        "Authorization": f"Bearer {KEY}",
        "X-Cron-Secret": CRON_SECRET,
        "Content-Type": "application/json",
    }
    req = urllib.request.Request(
        f"{SUPABASE_URL}/functions/v1/medication-reminder-cron",
        data=b"{}",
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        d = json.loads(r.read().decode())
    return d["debug"]["currentHHMM"]


def add_minutes(hhmm: str, m: int) -> str:
    h, mm = map(int, hhmm.split(":"))
    mm += m
    h += mm // 60
    mm %= 60
    return f"{h % 24:02d}:{mm:02d}"


def main() -> int:
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    started = time.time()

    print(f"Building load-test fixture (N={n})...")

    print("  Creating GPs...")
    gps = []
    for i in range(n):
        uid = create_auth_user(f"loadtest_gp_{i}_{int(started)}@test.local", "doctor")
        create_doctor_profile(uid, f"Dr LoadGP {i}", "gp", i)
        gps.append(uid)

    print("  Creating nurses...")
    nurses = []
    for i in range(n):
        uid = create_auth_user(f"loadtest_nurse_{i}_{int(started)}@test.local", "doctor")
        create_doctor_profile(uid, f"Dr LoadNurse {i}", "nurse", i)
        nurses.append(uid)

    print("  Creating patient sessions...")
    sessions = [create_patient_session(i) for i in range(n)]

    print("  Creating Royal consultations...")
    consultations = [
        create_consultation(gps[i], sessions[i], i) for i in range(n)
    ]

    eat_now = get_eat_minute()
    fire_at = add_minutes(eat_now, 2)
    print(f"\nEAT now: {eat_now}, scheduling all {n} timetables to fire at {fire_at}.")

    print("  Creating medication timetables...")
    timetables = [
        create_timetable(consultations[i], gps[i], sessions[i], fire_at, i)
        for i in range(n)
    ]

    elapsed = time.time() - started
    print(f"\n✓ Fixture built in {elapsed:.1f}s")
    print(f"  GPs:           {n}")
    print(f"  Nurses:        {n}")
    print(f"  Sessions:      {n}")
    print(f"  Consultations: {n}")
    print(f"  Timetables:    {n}")
    print(f"  Fire window:   {fire_at} EAT (~2 minutes from now)")
    print(
        f"\nWatch via:\n"
        f"  curl -s -G '{SUPABASE_URL}/rest/v1/medication_reminder_events' "
        f"--data-urlencode 'scheduled_time=eq.{fire_at}' "
        f"--data-urlencode 'select=event_id,status,nurse_id,scheduled_time' "
        f"-H 'apikey: <KEY>' -H 'Authorization: Bearer <KEY>'"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
