"""Daily Cloud Run health digest: reads the last day of logs and emails a summary through Resend."""
import argparse
import html
import json
import os
import re
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from urllib.parse import quote

from google.cloud import logging as cloud_logging

PROJECT = os.environ.get("GCP_PROJECT", "hak-talent-mapping")
SERVICE = os.environ.get("SERVICE", "lightmove")
WEB = os.environ.get("HEALTH_HOST", "beta.uncava.com")
SERVICE_FILTER = f'resource.type="cloud_run_revision" AND resource.labels.service_name="{SERVICE}"'
VOLATILE = re.compile(r"[0-9a-f]{8}-[0-9a-f-]{27}|\b\d{3,}\b|@[0-9a-f]{5,}")


def logs_link(query: str) -> str:
    return f"https://console.cloud.google.com/logs/query;query={quote(query)}?project={PROJECT}"


def signature(entry) -> str:
    payload = entry.payload if isinstance(entry.payload, dict) else {"message": str(entry.payload)}
    message = (payload.get("message") or "").splitlines()[0][:160] if payload.get("message") else "(no message)"
    logger = payload.get("logger", "")
    return f"{logger.rsplit('.', 1)[-1]}: {VOLATILE.sub('#', message)}" if logger else VOLATILE.sub("#", message)


def collect(start: datetime, end: datetime) -> dict:
    client = cloud_logging.Client(project=PROJECT)
    window = f'timestamp>="{start.isoformat()}" AND timestamp<"{end.isoformat()}"'
    statuses, urls_5xx, latencies = Counter(), Counter(), []
    revisions = set()
    for entry in client.list_entries(filter_=f'{SERVICE_FILTER} AND {window} AND logName:"run.googleapis.com%2Frequests"',
                                     page_size=1000):
        request = entry.http_request or {}
        status = int(request.get("status", 0))
        statuses[f"{status // 100}xx"] += 1
        if status >= 500:
            urls_5xx[f'{request.get("requestMethod", "")} {(request.get("requestUrl") or "").split("?")[0]}'] += 1
        latency = str(request.get("latency", "")).rstrip("s")
        if latency:
            latencies.append(float(latency))
        revisions.add(entry.resource.labels.get("revision_name", ""))

    groups = defaultdict(lambda: {"count": 0, "first": None, "last": None})
    for severity in ("ERROR", "WARNING"):
        for entry in client.list_entries(
                filter_=f'{SERVICE_FILTER} AND {window} AND severity={severity} AND NOT logName:"run.googleapis.com%2Frequests"',
                page_size=1000):
            group = groups[(severity, signature(entry))]
            group["count"] += 1
            group["first"] = min(filter(None, [group["first"], entry.timestamp]))
            group["last"] = max(filter(None, [group["last"], entry.timestamp]))
    latencies.sort()
    p95 = latencies[int(len(latencies) * 0.95) - 1] if latencies else 0.0
    return {"statuses": statuses, "urls_5xx": urls_5xx, "p95": p95, "revisions": sorted(r for r in revisions if r),
            "groups": groups, "total": sum(statuses.values())}


def render(data: dict, start: datetime, end: datetime) -> tuple[str, str]:
    errors = {k: v for k, v in data["groups"].items() if k[0] == "ERROR"}
    warnings = {k: v for k, v in data["groups"].items() if k[0] == "WARNING"}
    error_count = sum(v["count"] for v in errors.values())
    server_errors = data["statuses"]["5xx"]
    healthy = error_count == 0 and server_errors == 0
    subject = (f"Uncava prod: all OK — {data['total']} requests" if healthy
               else f"Uncava prod: {error_count} errors, {server_errors} 5xx in 24h")

    def rows(items, severity):
        link_base = f'{SERVICE_FILTER} AND severity={severity} AND NOT logName:"run.googleapis.com%2Frequests"'
        ordered = sorted(items.items(), key=lambda kv: -kv[1]["count"])[:10]
        return "".join(
            f'<tr><td style="padding:4px 12px 4px 0"><a href="{logs_link(link_base)}">{html.escape(sig)}</a></td>'
            f'<td style="padding:4px 12px;text-align:right">{v["count"]}</td>'
            f'<td style="padding:4px 0;color:#666">{v["first"]:%H:%M} – {v["last"]:%H:%M} UTC</td></tr>'
            for (_, sig), v in ordered)

    parts = [f'<h2 style="margin:0 0 8px">{"✅ All OK" if healthy else "⚠️ Errors in the last 24h"}</h2>',
             f'<p style="color:#666;margin:0 0 16px">{start:%d %b %H:%M} – {end:%d %b %H:%M} UTC · '
             f'{data["total"]} requests · 2xx {data["statuses"]["2xx"]} · 4xx {data["statuses"]["4xx"]} · '
             f'5xx {server_errors} · p95 {data["p95"]:.2f}s · revision {", ".join(data["revisions"]) or "none"}</p>']
    if errors:
        parts += ["<h3>Errors</h3><table>", rows(errors, "ERROR"), "</table>"]
    if data["urls_5xx"]:
        parts += ["<h3>5xx responses</h3><ul>"] + [f"<li>{html.escape(u)} × {n}</li>" for u, n in data["urls_5xx"].most_common(10)] + ["</ul>"]
    if warnings:
        parts += ["<h3>Warnings (top 10)</h3><table>", rows(warnings, "WARNING"), "</table>"]
    parts.append(f'<p style="color:#666;margin-top:24px"><a href="https://{WEB}">{WEB}</a> · sent by lightmove-log-digest</p>')

    text = subject + "\n" + "\n".join(f'{v["count"]:>5}  {sig}' for (_, sig), v in sorted(errors.items(), key=lambda kv: -kv[1]["count"]))
    return subject, f'<div style="font-family:system-ui,sans-serif;font-size:14px">{"".join(parts)}</div>', text


def send(subject: str, body: str, text: str, recipients: list[str]) -> None:
    request = urllib.request.Request(
        "https://api.resend.com/emails",
        data=json.dumps({"from": os.environ["EMAIL_FROM"], "to": recipients, "subject": subject, "html": body, "text": text}).encode(),
        headers={"Authorization": f"Bearer {os.environ['RESEND_API_KEY']}", "Content-Type": "application/json",
                 "User-Agent": "lightmove-log-digest/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            print(f"sent to {len(recipients)} recipients: HTTP {response.status}")
    except urllib.error.HTTPError as refused:
        raise SystemExit(f"Resend refused the digest: HTTP {refused.code} {refused.read().decode()[:300]}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="print the email instead of sending it")
    parser.add_argument("--hours", type=int, default=24)
    parser.add_argument("--to", help="override DIGEST_TO")
    args = parser.parse_args()

    end = datetime.now(timezone.utc)
    start = end - timedelta(hours=args.hours)
    subject, body, text = render(collect(start, end), start, end)
    if args.dry_run:
        print(subject, "\n", text)
    else:
        send(subject, body, text, [a.strip() for a in (args.to or os.environ["DIGEST_TO"]).split(",") if a.strip()])
