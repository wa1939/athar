"""
Parse an SMS Exporter .txt export and bulk-insert into an Android emulator
SMS DB via direct sqlite3 access (requires `adb root`). Used for E2E testing.

Usage:
    python push_sms_sample.py <export.txt> [--limit N] [--thread-id 1]
"""
import argparse
import re
import subprocess
import sys
import os
from datetime import datetime
from pathlib import Path


HEADER_RE = re.compile(
    r"Received from (\S+) on (\d{4}-\d{2}-\d{2} \d{2}:\d{2})"
)
SEPARATOR = "-" * 53


def parse_messages(path: Path):
    text = path.read_text(encoding="utf-8", errors="replace")
    blocks = text.split(SEPARATOR)
    out = []
    for block in blocks:
        m = HEADER_RE.search(block)
        if not m:
            continue
        sender = m.group(1)
        ts = datetime.strptime(m.group(2), "%Y-%m-%d %H:%M")
        body_start = m.end()
        body = block[body_start:].strip()
        if not body:
            continue
        out.append((sender, ts, body))
    return out


def sql_escape(s: str) -> str:
    return s.replace("'", "''")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("export", type=Path)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--adb",
                    default=str(Path.home() / "Android/Sdk/platform-tools/adb.exe"))
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--thread-id", type=int, default=1)
    ap.add_argument("--sender-override", default="AlRajhiBank")
    args = ap.parse_args()

    if not args.export.exists():
        print(f"file not found: {args.export}", file=sys.stderr)
        sys.exit(2)

    msgs = parse_messages(args.export)
    print(f"parsed {len(msgs)} messages from {args.export.name}")
    if args.limit > 0:
        msgs = msgs[:args.limit]
        print(f"limiting to first {len(msgs)}")

    out_dir = Path(__file__).parent
    sql_path = out_dir / "_sms_insert.sql"
    sql_lines = ["BEGIN TRANSACTION;"]
    for sender, ts, body in msgs:
        epoch_ms = int(ts.timestamp() * 1000)
        address = args.sender_override or sender
        sql_lines.append(
            f"INSERT INTO sms (thread_id, address, date, date_sent, read, "
            f"status, type, body, seen) VALUES "
            f"({args.thread_id}, '{sql_escape(address)}', {epoch_ms}, {epoch_ms}, "
            f"1, -1, 1, '{sql_escape(body)}', 1);"
        )
    sql_lines.append("COMMIT;")
    sql_path.write_text("\n".join(sql_lines), encoding="utf-8")
    print(f"wrote {len(msgs)} INSERTs to {sql_path.name}")

    env = dict(**os.environ, MSYS_NO_PATHCONV="1")
    db = "/data/data/com.android.providers.telephony/databases/mmssms.db"
    remote = "/sdcard/_sms_insert.sql"

    # push to device
    r = subprocess.run(
        [args.adb, "-s", args.serial, "push", str(sql_path), remote],
        env=env, capture_output=True, text=True, timeout=60,
    )
    if r.returncode != 0:
        print("push failed:", r.stderr)
        sys.exit(3)
    print("pushed SQL to device")

    # run
    r = subprocess.run(
        [args.adb, "-s", args.serial, "shell",
         f"sqlite3 {db} < {remote}"],
        env=env, capture_output=True, text=True, timeout=600,
    )
    if r.returncode != 0:
        print("sqlite3 failed:", r.stderr[:500])
        sys.exit(4)
    print("sqlite3 batch insert OK")

    # cleanup
    subprocess.run([args.adb, "-s", args.serial, "shell", "rm", remote],
                   env=env, capture_output=True, text=True, timeout=15)
    sql_path.unlink(missing_ok=True)

    # count
    r = subprocess.run(
        [args.adb, "-s", args.serial, "shell",
         f"sqlite3 {db} 'SELECT COUNT(*) FROM sms'"],
        env=env, capture_output=True, text=True, timeout=15,
    )
    print(f"total SMS rows: {r.stdout.strip()}")


if __name__ == "__main__":
    main()
