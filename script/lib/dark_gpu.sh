LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"

log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg" | tee -a "$LOG_FILE"
}

gpu_id=""
if dumpsys SurfaceFlinger 2>/dev/null | grep -q 'GLES:'; then
    opengl_renderer=$(dumpsys SurfaceFlinger | grep 'GLES:' | awk '{print $3, $4}' | sed 's/,//' | tr -d '[:space:]')
    gpu_id=$(dumpsys SurfaceFlinger | grep 'GLES:' | awk '{print $5}' | sed 's/,//' | tr -d '[:space:]')
fi
if [ -z "$gpu_id" ] && [ -f "/sys/class/kgsl/kgsl-3d0/gpu_model" ]; then
    gpu_id=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model | tr -d '[:space:]')
    opengl_renderer="(Unknown OpenGL Renderer)"
fi
if [ -n "$gpu_id" ]; then
    correct_config="1 1 $gpu_id"
    log_msg "✅ GPU Detected: $opengl_renderer - $gpu_id"

    egl_dirs=(
        "$MODPATH/system/lib/egl"
        "$MODPATH/system/lib64/egl"
        "$MODPATH/system/vendor/lib/egl"
        "$MODPATH/system/vendor/lib64/egl"
    )

    for lib_dir in "${egl_dirs[@]}"; do
        [ ! -d "$lib_dir" ] && mkdir -p "$lib_dir"
        egl_cfg="$lib_dir/egl.cfg"

        if [ -f "$egl_cfg" ]; then
            current_config=$(awk '{print $1 " " $2 " " $3}' "$egl_cfg" 2>/dev/null)
            if [ "$current_config" != "$correct_config" ]; then
                echo "$correct_config" > "$egl_cfg"
                log_msg "🔄 Updated: $egl_cfg"
            fi
        else
            echo "$correct_config" > "$egl_cfg"
            log_msg "🆕 Created: $egl_cfg"
        fi
    done

    log_msg "🚀 GPU Tweak Applied Successfully!"
else
    log_msg "⚠️ GPU Model Not Found!"
fi
