# HUD gaming add-ins

## 1. Identity and UI

The native HUD's Add-ins page is `HudLab.kt`. A horizontally scrollable category strip selects Focus, Bypass Charging, Touch Master, Game Network, or Magic Box. Only the selected category is rendered. `HudGamingCards.kt` supplies notification glow settings, network information, and custom tap editing. Controls use 48 dp minimum targets and 12 sp labels; the existing bounded HUD window and its portrait/landscape sizing are unchanged.

## 2. State and lifetime

`HudController` owns one `HudGamingAddins` session in the HUD process. The process-local registry lets the isolated add-in UI and notification listener find that session without adding hardware dependencies to Compose. StateFlow exposes glow preferences, notification listener connection, requested Wi-Fi lock, macro points, interval and execution state. Notification access is explicitly granted in Android Settings; the app never enables it silently.

`HudNotificationGlowService` runs in `:hud` and handles incoming callbacks directly. It ignores own/ongoing/group-summary notifications, duplicate callbacks, stale posts and reconnection replay. It reads only notification metadata, not message extras. A bounded in-memory key/timestamp map is discarded with the service. Glow uses four narrow, non-touchable edge windows with a 1.25 second lifetime. Each window uses one quarter of Android's reported maximum obscuring opacity so combined edges remain below the touch pass-through limit. Delivery depends on Android and the sender; no push-latency guarantee is made.

The Wi-Fi lock and glow are released on screen off and HUD teardown. Macro execution is stopped on screen off and teardown. Driver and focus journals are recovered when the HUD next starts after an unexpected process death. Normal teardown restores immediately; there is no claim of immediate recovery after force-stop or a kernel crash.

## 3. Parameters

- Glow defaults on and only pulses while Focus/DND or banner suppression is active. Cyan, blue and amber are persisted choices.
- Focus offers priority/silent/off and banner suppression. The Summary Focus action controls silent DND and banners together. The original settings are journaled before mutation and changes are read back.
- Hardware bypass and driver touch/game mode are opt-in and available only when a recognized writable boolean interface is readable. A flag file or charging-chip identity cannot establish support.
- Game Network shows the active transport, Android validation, available Wi-Fi/mobile transports and device-wide receive throughput. Wi-Fi low-latency lock is an explicit request on Android 10+. A held lock does not prove the OS granted a latency improvement. It can increase power use. No global game packet routing or system network tuning is changed.
- Network tests are user-triggered TCP connections to 1.1.1.1:443. Results describe connection latency, jitter and failed connection attempts, not game-server RTT, UDP packet loss or bandwidth. Wi-Fi and mobile bandwidth are not bonded; arbitrary game traffic requires a compatible transport/proxy/server before that can be supported.
- Magic Box saves 1â€“5 normalized screen points, defaults to the center, and supports 750â€“5000 ms delay (default 1000 ms). Each explicitly started run is bounded to 30 taps and 60 seconds, including time paused with HUD open. It stops on package/geometry changes. The existing game-specific dialogue assistant remains separate and is mutually exclusive with custom sequences. Custom sequences do not recognize dialogue automatically.

## 4. Hardware and command boundaries

`HudDriverSession` accepts only explicit known boolean interfaces and values 0/1. Touch adapters are Qualcomm `msm_performance/.../touchboost`, OnePlus `touchpanel/game_switch_enable`, and known touch-device `game_mode` paths. Hardware bypass adapters use `power_supply/battery/bypass_charging` or `charge_bypass`. Missing, non-boolean or inaccessible nodes are unsupported. Generic scheduler boosts and charging-disable nodes are not substituted. No thermal protection, charging-current target or KPM is modified.

`HudFocusSession` owns root `cmd notification set_dnd` and the global banner setting. It validates values and reads the resulting state. Per-key journals use compare-before-restore so external user changes win. `HudMagicBox` verifies Android's actual foreground package inside the same root command as each tap, validates the package string, and destroys its child on cancellation. UI never executes shell commands.

## 5. Error handling and validation

Unsupported data is displayed as unavailable and controls are disabled. Registration of a hardware node is not evidence of measured input latency or physical charging power flow. Touch-pad metrics are UI event intervals, not digitizer hardware sample rate. Root failures and driver write failures cannot be promoted to success using marker files.

Unit tests cover session restoration ownership, macro app/screen/rotation guards, coordinate bounds and shell injection rejection. Release compilation, unit tests, Android locale checks and the repository-wide locale validator are required. The full validator currently has unrelated legacy failures; record those separately from new add-in findings. Device tests must be scoped to X698, remain on the Lynx foreground for UI automation, avoid running macros in other apps, and must not flash modules or change thermal protection.
