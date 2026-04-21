#!/usr/bin/env python3
"""Lean live-gateway compatibility smoke checks for Hermes Courier."""

from __future__ import annotations

import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass
class CheckResult:
    name: str
    status: str
    detail: str


def env_bool(name: str, default: bool = False) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def normalize_list_payload(payload: Any) -> tuple[str | None, list[Any] | None]:
    if isinstance(payload, list):
        return "array", payload
    if isinstance(payload, dict):
        for key in ("items", "data", "results"):
            value = payload.get(key)
            if isinstance(value, list):
                return key, value
    return None, None


def summarize_json(payload: Any) -> str:
    if isinstance(payload, dict):
        keys = ", ".join(sorted(payload.keys()))
        return f"object keys=[{keys}]"
    if isinstance(payload, list):
        return f"array len={len(payload)}"
    return f"type={type(payload).__name__}"


class LiveSmoke:
    def __init__(self) -> None:
        base = os.environ.get("HERMES_LIVE_GATEWAY_BASE_URL", "").strip().rstrip("/")
        if not base:
            print("live-gateway-smoke-test: SKIP (set HERMES_LIVE_GATEWAY_BASE_URL)")
            sys.exit(0)
        self.base = base
        self.timeout = float(os.environ.get("HERMES_LIVE_GATEWAY_TIMEOUT_SECONDS", "20"))
        self.bearer = os.environ.get("HERMES_LIVE_GATEWAY_BEARER_TOKEN")
        self.auth_mode = os.environ.get("HERMES_LIVE_GATEWAY_AUTH_MODE", "auto").strip().lower()
        self.seed_session_id = os.environ.get("HERMES_LIVE_GATEWAY_SESSION_ID", "").strip()
        self.seed_approval_id = os.environ.get("HERMES_LIVE_GATEWAY_APPROVAL_ID", "").strip()
        self.send_message = os.environ.get("HERMES_LIVE_GATEWAY_CONVERSATION_MESSAGE", "live verification ping")
        self.allow_mutating = env_bool("HERMES_LIVE_GATEWAY_ALLOW_MUTATING", default=False)

        insecure = env_bool("HERMES_LIVE_GATEWAY_CURL_INSECURE", default=False)
        ctx = ssl.create_default_context()
        if insecure:
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
        self.ssl_context = ctx

        self.results: list[CheckResult] = []
        self.session_id: str | None = self.seed_session_id or None
        self.approval_id: str | None = self.seed_approval_id or None

    def url(self, path: str) -> str:
        return urllib.parse.urljoin(self.base + "/", path.lstrip("/"))

    def request(self, method: str, path: str, body: dict[str, Any] | None = None, auth: bool = True) -> tuple[int, bytes]:
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(self.url(path), method=method.upper(), data=data)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if auth and self.bearer:
            req.add_header("Authorization", f"Bearer {self.bearer}")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_context) as resp:
                return int(resp.status), resp.read()
        except urllib.error.HTTPError as err:
            return int(err.code), err.read()
        except Exception as err:  # noqa: BLE001
            return 0, str(err).encode("utf-8")

    def add(self, name: str, status: str, detail: str) -> None:
        self.results.append(CheckResult(name=name, status=status, detail=detail))

    def parse_json(self, raw: bytes) -> Any | None:
        if not raw:
            return None
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:  # noqa: BLE001
            return None

    def run(self) -> int:
        self.check_bootstrap_or_token()
        self.check_dashboard()
        self.check_sessions_and_detail()
        self.check_approvals_and_decision()
        self.check_conversation_list_and_send()
        self.check_events_reachability()
        self.print_report()
        return 0 if all(r.status in {"ok", "skipped", "unsupported"} for r in self.results) else 1

    def check_bootstrap_or_token(self) -> None:
        if self.bearer and self.auth_mode in {"auto", "token"}:
            self.add("auth/bootstrap", "ok", "Using provided bearer token from HERMES_LIVE_GATEWAY_BEARER_TOKEN.")
            return
        if self.auth_mode == "token":
            self.add("auth/bootstrap", "failed", "Auth mode set to token, but no bearer token was provided.")
            return
        self.add("auth/bootstrap", "skipped", "Challenge-response bootstrap requires client signing context; provide bearer token for smoke checks.")

    def check_dashboard(self) -> None:
        code, raw = self.request("GET", "/v1/dashboard")
        if code == 0:
            self.add("dashboard", "failed", f"Network error: {raw.decode(errors='replace')}")
            return
        if code in {401, 403}:
            self.add("dashboard", "failed", f"HTTP {code}; provide valid bearer token or gateway credentials.")
            return
        if code == 404:
            self.add("dashboard", "unsupported", "HTTP 404 on /v1/dashboard.")
            return
        payload = self.parse_json(raw)
        if code // 100 == 2 and isinstance(payload, dict):
            required = {"activeSessionCount", "pendingApprovalCount", "lastSyncLabel", "connectionState"}
            missing = sorted(required - set(payload.keys()))
            if missing:
                self.add("dashboard", "drift", f"HTTP {code}; missing fields: {', '.join(missing)}")
            else:
                self.add("dashboard", "ok", f"HTTP {code}; dashboard shape matches decoder expectations.")
            return
        self.add("dashboard", "failed", f"HTTP {code}; expected dashboard object, got {summarize_json(payload)}")

    def check_sessions_and_detail(self) -> None:
        code, raw = self.request("GET", "/v1/sessions")
        if code == 404:
            self.add("sessions list", "unsupported", "HTTP 404 on /v1/sessions.")
            self.add("session detail", "skipped", "Skipped because sessions list endpoint is unsupported.")
            self.add("session-control action candidates", "skipped", "Skipped because sessions list endpoint is unsupported.")
            return
        if code // 100 != 2:
            self.add("sessions list", "failed", f"HTTP {code} on /v1/sessions.")
            self.add("session detail", "skipped", "Skipped because sessions list failed.")
            self.add("session-control action candidates", "skipped", "Skipped because sessions list failed.")
            return
        payload = self.parse_json(raw)
        wrapper, items = normalize_list_payload(payload)
        if items is None:
            self.add("sessions list", "drift", f"HTTP {code}; expected array/items/data/results, got {summarize_json(payload)}")
            self.add("session detail", "skipped", "Skipped because sessions list payload shape drifted.")
            self.add("session-control action candidates", "skipped", "Skipped because sessions list payload shape drifted.")
            return
        self.add("sessions list", "ok", f"HTTP {code}; wrapper style '{wrapper}' accepted by decoders.")
        if self.session_id is None and items:
            first = items[0]
            if isinstance(first, dict):
                self.session_id = str(first.get("sessionId", "")).strip() or None
        if not self.session_id:
            self.add("session detail", "skipped", "No session ID available from env or sessions list.")
            self.add("session-control action candidates", "skipped", "No session ID available from env or sessions list.")
            return
        detail_code, detail_raw = self.request("GET", f"/v1/sessions/{self.session_id}")
        detail_payload = self.parse_json(detail_raw)
        if detail_code == 404:
            self.add("session detail", "unsupported", f"HTTP 404 on /v1/sessions/{self.session_id}.")
        elif detail_code // 100 == 2 and isinstance(detail_payload, dict) and "sessionId" in detail_payload:
            self.add("session detail", "ok", f"HTTP {detail_code}; session detail shape includes sessionId.")
        elif detail_code // 100 == 2:
            self.add("session detail", "drift", f"HTTP {detail_code}; unexpected shape {summarize_json(detail_payload)}")
        else:
            self.add("session detail", "failed", f"HTTP {detail_code} on session detail.")
        self.check_session_control_candidates(self.session_id)

    def check_session_control_candidates(self, session_id: str) -> None:
        candidates = [
            (f"/v1/sessions/{session_id}/actions", {"action": "pause"}),
            (f"/v1/sessions/{session_id}/pause", {}),
        ]
        seen_unsupported = 0
        shape_ok = False
        detail_notes: list[str] = []
        if not self.allow_mutating:
            self.add("session-control action candidates", "skipped", "Set HERMES_LIVE_GATEWAY_ALLOW_MUTATING=1 to exercise POST action routes.")
            return
        for path, body in candidates:
            code, raw = self.request("POST", path, body=body)
            payload = self.parse_json(raw)
            if code in {404, 405}:
                seen_unsupported += 1
                detail_notes.append(f"{path}: HTTP {code}")
                continue
            if code in {200, 201, 202, 204}:
                if code == 204 or payload is None:
                    detail_notes.append(f"{path}: HTTP {code} empty-body accepted")
                    shape_ok = True
                    continue
                if isinstance(payload, dict) and ("endpoint" in payload or "supported" in payload):
                    detail_notes.append(f"{path}: HTTP {code} returned endpoint/supported fields")
                    shape_ok = True
                else:
                    detail_notes.append(f"{path}: HTTP {code} payload missing endpoint/supported markers")
                continue
            detail_notes.append(f"{path}: HTTP {code}")
        if shape_ok:
            self.add("session-control action candidates", "ok", "; ".join(detail_notes))
        elif seen_unsupported == len(candidates):
            self.add("session-control action candidates", "unsupported", "; ".join(detail_notes))
        else:
            self.add("session-control action candidates", "drift", "; ".join(detail_notes))

    def check_approvals_and_decision(self) -> None:
        code, raw = self.request("GET", "/v1/approvals")
        if code == 404:
            self.add("approvals list", "unsupported", "HTTP 404 on /v1/approvals.")
            self.add("approval decision", "skipped", "Skipped because approvals endpoint is unsupported.")
            return
        if code // 100 != 2:
            self.add("approvals list", "failed", f"HTTP {code} on /v1/approvals.")
            self.add("approval decision", "skipped", "Skipped because approvals list failed.")
            return
        payload = self.parse_json(raw)
        wrapper, items = normalize_list_payload(payload)
        if items is None:
            self.add("approvals list", "drift", f"HTTP {code}; expected array/items/data/results, got {summarize_json(payload)}")
            self.add("approval decision", "skipped", "Skipped because approvals payload shape drifted.")
            return
        self.add("approvals list", "ok", f"HTTP {code}; wrapper style '{wrapper}' accepted by decoders.")
        if self.approval_id is None and items:
            first = items[0]
            if isinstance(first, dict):
                self.approval_id = str(first.get("approvalId", "")).strip() or None
        if not self.allow_mutating:
            self.add("approval decision", "skipped", "Set HERMES_LIVE_GATEWAY_ALLOW_MUTATING=1 to exercise approval decision route.")
            return
        if not self.approval_id:
            self.add("approval decision", "skipped", "No approval ID available from env or approvals list.")
            return
        decision_code, decision_raw = self.request(
            "POST",
            f"/v1/approvals/{self.approval_id}/decision",
            body={"decision": "deny", "reason": "live smoke compatibility check"},
        )
        decision_payload = self.parse_json(decision_raw)
        if decision_code == 404:
            self.add("approval decision", "unsupported", "HTTP 404 on approval decision route.")
        elif decision_code in {200, 201, 202, 204}:
            if decision_code == 204 or decision_payload is None:
                self.add("approval decision", "ok", f"HTTP {decision_code}; empty-body success accepted by client.")
            elif isinstance(decision_payload, dict) and {"approvalId", "action", "status"}.issubset(decision_payload.keys()):
                self.add("approval decision", "ok", f"HTTP {decision_code}; response shape matches approval-action decoder.")
            else:
                self.add("approval decision", "drift", f"HTTP {decision_code}; unexpected payload {summarize_json(decision_payload)}")
        else:
            self.add("approval decision", "failed", f"HTTP {decision_code} on approval decision.")

    def check_conversation_list_and_send(self) -> None:
        list_code, list_raw = self.request("GET", "/v1/conversation")
        if list_code == 404:
            self.add("conversation list", "unsupported", "HTTP 404 on /v1/conversation.")
            self.add("conversation send", "skipped", "Skipped because conversation endpoint is unsupported.")
            return
        if list_code // 100 != 2:
            self.add("conversation list", "failed", f"HTTP {list_code} on /v1/conversation.")
            self.add("conversation send", "skipped", "Skipped because conversation list failed.")
            return
        payload = self.parse_json(list_raw)
        wrapper, items = normalize_list_payload(payload)
        if items is None:
            self.add("conversation list", "drift", f"HTTP {list_code}; expected array/items/data/results, got {summarize_json(payload)}")
        else:
            self.add("conversation list", "ok", f"HTTP {list_code}; wrapper style '{wrapper}' accepted by decoders.")
        if not self.allow_mutating:
            self.add("conversation send", "skipped", "Set HERMES_LIVE_GATEWAY_ALLOW_MUTATING=1 to exercise conversation POST.")
            return
        send_code, send_raw = self.request("POST", "/v1/conversation", body={"body": self.send_message})
        send_payload = self.parse_json(send_raw)
        if send_code == 404:
            self.add("conversation send", "unsupported", "HTTP 404 on conversation POST.")
        elif send_code in {200, 201, 202, 204}:
            if send_code == 204 or send_payload is None:
                self.add("conversation send", "ok", f"HTTP {send_code}; empty-body accepted by fallback path.")
            elif isinstance(send_payload, dict) and {"eventId", "author", "body"}.issubset(send_payload.keys()):
                self.add("conversation send", "ok", f"HTTP {send_code}; response shape matches conversation decoder.")
            else:
                self.add("conversation send", "drift", f"HTTP {send_code}; unexpected payload {summarize_json(send_payload)}")
        else:
            self.add("conversation send", "failed", f"HTTP {send_code} on conversation POST.")

    def check_events_reachability(self) -> None:
        code, raw = self.request("GET", "/v1/events")
        if code in {101, 200, 204, 401, 403, 426}:
            self.add("realtime/events reachability", "ok", f"HTTP {code}; endpoint reachable (gateway-specific handshake behavior).")
            return
        if code == 404:
            self.add("realtime/events reachability", "unsupported", "HTTP 404 on /v1/events.")
            return
        if code == 0:
            self.add("realtime/events reachability", "failed", f"Network error: {raw.decode(errors='replace')}")
            return
        self.add("realtime/events reachability", "failed", f"HTTP {code} on /v1/events.")

    def print_report(self) -> None:
        print(f"live-gateway-smoke-test: base={self.base}")
        print(f"live-gateway-smoke-test: auth_mode={self.auth_mode} mutating={'enabled' if self.allow_mutating else 'disabled'}")
        print("live-gateway-smoke-test: results")
        for item in self.results:
            print(f" - [{item.status}] {item.name}: {item.detail}")
        failures = [r for r in self.results if r.status in {"failed", "drift"}]
        if failures:
            print(f"live-gateway-smoke-test: FAILED ({len(failures)} check(s) failed or drifted)")
        else:
            print("live-gateway-smoke-test: OK (no failed/drift checks)")


def main() -> int:
    return LiveSmoke().run()


if __name__ == "__main__":
    raise SystemExit(main())
