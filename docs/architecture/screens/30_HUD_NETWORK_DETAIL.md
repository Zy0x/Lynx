# HUD Network detail

## 1. Identity and structure

`HudNetworkPage.kt` uses Connection / Settings / Test tabs. `HudGameNetworkControls(showSettings)` composes either transport/validation/throughput status or the existing optimization switches. Test contains a reflowing latency/jitter/failure grid and the manual probe action.

## 2. State and flow

Manual sample and probe-busy state stay above the tab boundary. Existing runtime flows supply Wi-Fi, Turbo, standby, TCP and QoS states. Throughput retains the one-second TrafficStats delta; no shell polling is added. Probe execution requires an explicit click.

## 3. Parameters and defaults

Connection opens first. All saved settings are preserved. The manual test retains its existing endpoint and controller semantics. Help text avoids claiming zero-time failover or universal TCP latency improvements.

## 4. Hardware mapping

Existing network/session controllers own Android connectivity requests, Wi-Fi locks and root TCP/QoS writes. No driver adapter, endpoint, rule ownership, sysfs path or tuning default changes. The TCP probe is not game-server RTT, UDP packet loss or bandwidth.

## 5. Boundaries and validation

QoS retains supported-target, root, package and busy guards. Wi-Fi capability checks remain. Navigation starts no probe and applies no optimization. Physical routing, radio behavior and network measurements are not tested in this local-only cycle.
