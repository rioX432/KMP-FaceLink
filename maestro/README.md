# Maestro E2E Tests

End-to-end smoke tests for the KMP-FaceLink Android sample app using [Maestro](https://maestro.mobile.dev/).

## Prerequisites

- Maestro CLI installed (`curl -Ls "https://get.maestro.mobile.dev" | bash`)
- Android emulator running or physical device connected
- Sample app installed: `./gradlew :androidApp:installDebug`

## Test Flows

| Flow | Description |
|---|---|
| `app_launch.yaml` | Verifies the app launches and the home screen is visible |
| `camera_permission.yaml` | Grants camera permission and checks the tracking UI appears |
| `face_tracking_start.yaml` | Starts face tracking, verifies active state, then stops |
| `mode_switch.yaml` | Switches between tracking modes (Face, Hand, Holistic, Actions, Voice) |
| `stop_and_release.yaml` | Starts tracking, stops, verifies idle state, and checks responsiveness |

## Running Tests

Run a single flow:

```bash
maestro test maestro/app_launch.yaml
```

Run all flows:

```bash
maestro test maestro/
```

## Notes

- Tests that start tracking (`face_tracking_start.yaml`, `stop_and_release.yaml`) pre-grant the camera permission via `clearState: true` (Maestro defaults all permissions to `allow`).
- On emulator without a camera, tracking may transition to ERROR state instead of TRACKING. The `camera_permission.yaml` flow uses `optional: true` assertions to handle this.
- `extendedWaitUntil` is used for tracker startup which may take a few seconds on slower devices.
