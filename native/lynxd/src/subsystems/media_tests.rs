use super::super::media_catalog::{quote, validate_audio_state};
use super::{MediaController, MODES};

#[test]
fn system_preserves_the_exact_original_values() {
    let original = vec!["".into(), "123".into(), "456".into()];
    assert_eq!(MediaController::targets("system", &original), original);
}

#[test]
fn compatibility_changes_only_codec2_listing() {
    let original = vec!["4".into(), "123".into(), "456".into()];
    assert_eq!(
        MediaController::targets("omx_compat", &original),
        ["0", "123", "456"]
    );
}

#[test]
fn ranking_preferences_retain_codec2_and_order_omx() {
    let original = vec![String::new(); 3];
    assert_eq!(
        MediaController::targets("prefer_hardware", &original),
        ["4", "0", "528"]
    );
    assert_eq!(
        MediaController::targets("prefer_software", &original),
        ["4", "256", "0"]
    );
    assert_eq!(MODES.len(), 4);
}

#[test]
fn audio_restart_gate_requires_a_known_normal_mode() {
    assert!(validate_audio_state("- Actual mode = MODE_NORMAL").is_ok());
    assert!(validate_audio_state("mAudioModeOwner: AudioModeInfo: mMode=MODE_NORMAL").is_ok());
    assert!(validate_audio_state("").is_err());
    assert!(validate_audio_state("history: MODE_NORMAL").is_err());
    for mode in [
        "MODE_IN_CALL",
        "MODE_IN_COMMUNICATION",
        "MODE_RINGTONE",
        "MODE_CALL_SCREENING",
    ] {
        assert!(validate_audio_state(&format!(
            "- Actual mode = MODE_NORMAL\n- Requested mode = {}",
            mode
        ))
        .is_err());
    }
}

#[test]
fn status_strings_escape_json_control_characters() {
    assert_eq!(quote("a\"b\nc"), "\"a\\\"b\\u000ac\"");
}

#[test]
fn invalid_cli_arguments_never_reach_a_property_write() {
    for command in [
        vec!["set", "omx_compat", "false", "--misspelled"],
        vec!["set", "invalid", "true"],
        vec!["set", "omx_compat", "yes"],
        vec!["restore", "--misspelled"],
        vec!["restart-player", "unexpected"],
        vec!["status", "unexpected"],
    ] {
        assert_eq!(
            MediaController::cli(&command.into_iter().map(str::to_owned).collect::<Vec<_>>()),
            1
        );
    }
}
