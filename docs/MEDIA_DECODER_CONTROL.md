# Media and decoder compatibility controls

## User interface

Companion exposes **Tools → Media & Decoders**. WebUI exposes the same feature in its Tools tab. Both surfaces use the native `lynxd media` controller and shared module configuration. Media selection is independent of CPU/GPU/performance profiles.

| Profile ID | Behavior |
| --- | --- |
| `system` | Restore the properties captured before this feature's first change in the current kernel boot. No fixed OEM values are guessed. |
| `omx_compat` | Hide Codec2 components using `debug.stagefright.ccodec=0`; use available legacy OMX implementations. |
| `prefer_hardware` | Keep Codec2 enabled and prioritize the default OMX hardware rank. |
| `prefer_software` | Keep Codec2 enabled and prioritize the default OMX software-video rank. |

The last two profiles are ranking preferences, not a guarantee that an application uses a specific codec. Codec-specific XML ranks and explicit application choices can override them. No codec binaries, FFmpeg or VLC implementation are installed by selecting these profiles. XML declarations only establish candidate availability, not working playback; Companion separately reads Android's MediaCodecList and the native status endpoint reports observed active media component names.

Factory defaults are:

```json
"media": {
  "decoder_mode": "system",
  "decoder_apply_on_boot": false
}
```

Select **OMX Compatibility**, enable **Apply selection at boot**, and confirm **Apply & Reload Media** to retain the tested workaround across boots after the new module is built and installed. Boot persistence is an explicit user choice. The boot hook runs after safe-mode checks; a default/disabled policy does not change media properties or restart a media service.

## Applying and restoring

- A confirmed apply checks the underlying implementation/property support and relevant declared AVC candidates, captures original values, writes properties with readback checks, atomically saves the selected configuration, then reloads the identified media player service. Only `mediaserver` / `mediaserver32` processes are targeted. Zygote, system_server, SurfaceFlinger, codec HALs and apps are not killed by the production media controller.
- Audio and telephony state gate the media-player restart. A call or unknown state defers it. A failure after properties/configuration were saved leaves a visible pending-restart status rather than claiming full completion. Reapply after the blocking condition clears.
- Reopen player applications after a media service reload so their own cached codec lists are refreshed. Property verification is distinct from proof that a particular video plays.
- Select **System / Restore Original** to restore this feature's saved values and disable boot application. Uninstall also attempts to restore owned values, and refuses automatic restoration if subsequent external property changes are detected.
- Snapshots and session metadata live in volatile `/dev/lynx_media/`, bound to kernel boot identity. A cold reboot starts a fresh snapshot from that boot's preexisting properties. Other modules' prior settings are part of that baseline; this feature does not infer or overwrite unknown OEM defaults.
- Mutation requests use the shared mode lock plus an exclusive-create media operation lock. A forcibly killed action can leave a stale volatile media lock; it fails closed until reboot rather than racing an automatic deletion. Property/session state rolls back if a property write, session save, or configuration save fails. An incomplete rollback is reported explicitly.

Compatibility mode disables all Codec2 listings, which can remove Codec2-only formats such as AV1, change audio paths, affect DRM support, or increase CPU consumption. The UI displays this warning before applying. Software preference can increase CPU load, power use and temperature. Unsupported strategies remain disabled; there is no universal automatic decoder switch on every app failure.

## Native interface

```sh
lynxd media status
lynxd media set omx_compat true --restart-player
lynxd media set prefer_hardware false --restart-player
lynxd media set prefer_software false --restart-player
lynxd media restore --restart-player
```

Omitting `--restart-player` stages properties/configuration and reports that the cached media list needs reloading. Mutation results are JSON (`ok`, optional technical `error`) with nonzero exit codes on failure. Status includes configured/session mode, boot preference, property readbacks, `session_property_verified`, pending restart, supported profile candidates, declared codecs and observed active components. A mode saved to configuration does not prove successful playback.

The implementation markers for Codec2 and OMX rank properties are probed before offering compatibility/ranking profiles. These probes do not replace playback tests on each ROM. The earlier shell marker probe was inconclusive. Running the compiled native controller on X698 subsequently found all required implementation markers and advertised all four strategies. Marker availability still does not establish successful playback. Local media labels and placeholders come from `tools/media_decoder_translations.json`; regenerate using `python tools/generate_media_decoder_locales.py`. The main locale validator includes media text/reference validation and scans additional WebUI JavaScript files.

## Measured evidence and source validation

The underlying OMX compatibility workaround was verified live on X698 with the user's previously failing Telegram clip; the active decoder changed to `OMX.google.h264.decoder` and the user confirmed playback. Five synthetic H.264 decoder/output-path trials also completed. See `VIDEO_MEMORY_AUDIT_20261015.md` for limits and the independently observed system_server/VMA crash. The new UI, native controller, boot hook, restore transaction and other ranking profiles are source changes only: no production daemon or Companion build, installation, module write, or reboot test was performed for them.

Completed static checks: Rust formatting/parser checks on new/extracted modules, JavaScript syntax checks, JSON/XML parsing, diff whitespace inspection, and media ID/EN catalog/placeholder/plural validation. The full locale validator still reports unrelated existing navigation accessibility, cooldown text, battery detail text, and charging-comment issues; the media-specific validator passes. Existing unrelated Kotlin file-budget violations also remain outside the modified Tools shell/new media files.

Before release, the build agent must compile the native Android Release target and Companion Release variant, validate all four modes on supported/unsupported ROMs, simulate property/configuration/restart/restore failures, check calls and unknown audio state, confirm safe-mode/dormant boot behavior and opt-in persistence, inspect actual decoder changes with real clips and DRM/audio/AV1 content, and inspect ID/EN accessibility and layout. No universal restart-prevention or memory-leak repair is claimed.

The earlier live diagnostic property was applied outside this new controller and is still temporary. Restore it with the saved Debug helper or cold reboot before establishing the feature's first original-value snapshot; otherwise the existing workaround is correctly treated as part of the baseline.
