# WebUI parity context for Hermes Courier

## 1) Hermes WebUI courier routes

`/home/iggut/.hermes/hermes-webui/api/courier_routes.py`

```py
if parsed.path.startswith("/v1/sessions/") and parsed.path.endswith("/actions"):
    parts = parsed.path.split("/")
    sid = parts[3] if len(parts) > 3 else ""
    action = str(body.get("action") or "").strip().lower() or "unknown"
    return j(handler, {
        "sessionId": sid,
        "action": action,
        "status": "unsupported",
        "detail": "Session-control actions are not mapped in Hermes WebUI yet.",
        "updatedAt": _iso_timestamp(time.time()),
        "supported": False,
        "endpoint": parsed.path,
    })

if parsed.path.startswith("/v1/sessions/"):
    parts = parsed.path.split("/")
    if len(parts) == 5:
        sid = parts[3]
        action = str(parts[4] or "").strip().lower()
        return j(handler, {
            "sessionId": sid,
            "action": action or "unknown",
            "status": "unsupported",
            "detail": "Session-control actions are not mapped in Hermes WebUI yet.",
            "updatedAt": _iso_timestamp(time.time()),
            "supported": False,
            "endpoint": parsed.path,
        })

if parsed.path == "/v1/conversation":
    body_text = str(body.get("body") or "").strip()
    if not body_text:
        return bad(handler, "body is required", 400)
    sid = str(body.get("sessionId") or "").strip() or _resolve_target_session_id("")
    if not sid:
        sessions = all_sessions()
        if sessions:
            sid = sessions[0]["session_id"]
        else:
            sid = new_session().session_id
    runtime_unavailable = _courier_runtime_unavailable_reason()
    if runtime_unavailable:
        return j(handler, _conversation_send_payload(
            sid,
            status="unsupported",
            body=f"Conversation send unsupported in current runtime: {runtime_unavailable}",
            supported=False,
            detail=runtime_unavailable,
        ))
```

## 2) Hermes WebUI skills routes

`/home/iggut/.hermes/hermes-webui/api/routes.py`

```py
if parsed.path == "/api/skills":
    from tools.skills_tool import skills_list as _skills_list
    raw = _skills_list()
    data = json.loads(raw) if isinstance(raw, str) else raw
    return j(handler, {"skills": data.get("skills", [])})

if parsed.path == "/api/skills/content":
    from tools.skills_tool import skill_view as _skill_view, SKILLS_DIR
    ...

if parsed.path == "/api/skills/save":
    return _handle_skill_save(handler, body)

if parsed.path == "/api/skills/delete":
    return _handle_skill_delete(handler, body)


def _handle_skill_save(handler, body):
    require(body, "name", "content")
    skill_name = body["name"].strip().lower().replace(" ", "-")
    category = body.get("category", "").strip()
    if category:
        skill_dir = SKILLS_DIR / category / skill_name
    else:
        skill_dir = SKILLS_DIR / skill_name
    skill_dir.mkdir(parents=True, exist_ok=True)
    skill_file = skill_dir / "SKILL.md"
    skill_file.write_text(body["content"], encoding="utf-8")
    return j(handler, {"ok": True, "name": skill_name, "path": str(skill_file)})


def _handle_skill_delete(handler, body):
    require(body, "name")
    from tools.skills_tool import SKILLS_DIR
    import shutil
    matches = list(SKILLS_DIR.rglob(f"{body['name']}/SKILL.md"))
    if not matches:
        return bad(handler, "Skill not found", 404)
    skill_dir = matches[0].parent
    shutil.rmtree(str(skill_dir))
    return j(handler, {"ok": True, "name": body["name"]})
```

## 3) Session control contract reference

`/home/iggut/.hermes/hermes-courier/shared/contract/hermes-courier-api.yaml`

```yaml
/v1/sessions/{sessionId}/actions:
  post:
    summary: Preferred session-control endpoint.
    body uses SessionControlRequest.action (pause, resume, terminate)

/v1/sessions/{sessionId}/{action}:
  post:
    summary: Alternate session-control style (empty JSON body)
    verbs: pause, resume, terminate
```

## 4) Current Courier Android observations
- `LibraryScreens.kt` renders Skills as read-only.
- `SessionDetailScreen.kt` disables session-control buttons when verification reports `unsupported`.
- `SessionsScreen.kt` and `HermesCourierViewModel.kt` already support live sessions/conversation, but the UX still needs parity tweaks for the user-reported gaps.
- Need production-ready implementation in the Android app only; do not change the webui repo.
