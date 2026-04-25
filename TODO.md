# Hermes Courier TODO

Generated based on codebase analysis and architecture shifts.

## Remaining Work (Chaquopy Integration)

- [ ] **Wire Chaquopy Plugin:** The `build.gradle.kts` needs to actually apply the `com.chaquo.python` plugin and configure the python block (e.g., specifying the srcDir pointing to `user_webui/` or similar).
- [ ] **Kotlin Interop:** Update Android Kotlin code to bootstrap the Python interpreter on application start (`Python.start(AndroidPlatform(this))`) inside `HermesCourierApplication.kt`.
- [ ] **Local API Service Integration:** Refactor the API client on Android to directly interop with the local Python application or bind to the locally exposed Flask/FastAPI server running inside the Chaquopy environment.

## Known Issues

- **Platform Divergence:** Android is now a "fat client" embedding the server via Chaquopy, while iOS remains a "thin client". This divergence means we need to carefully manage the `shared/contract` to assure both act identically from a UI perspective despite drastically different backend locations.
- **Local Settings:** The Android settings UI might still show a "gateway URL" input which is now obsolete for local backend mode. This needs to be conditionally hidden or repurposed for remote backup.

## Future Improvements

- **Biometric Authentication:** Implement gating for sensitive actions (approvals).
- **Push Notifications:** Add local push notifications (on Android) driven by the embedded Python agent.
- **Offline Capabilities:** Local caching for logs, queued actions, and offline-safe drafts, which may now be much easier on Android given the local backend.
