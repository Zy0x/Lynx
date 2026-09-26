FILE="/sdcard/Android/data/com.miHoYo.GenshinImpact/files/hardware_model_config.json"
if [ -f "$FILE" ]; then
    echo '{
        "configs": [
            {
                "hardwareModel": "'$(getprop ro.product.manufacturer)' '$(getprop ro.product.model)'",
                "HyperThreadingCount": 8,
                "littleCoreCount": 0,
                "bigCoreCount": 8,
                "littleCoreMask": 0,
                "bigCoreMask": 524288,
                "AffinityMask": 524288,
                "vulkanFlag": 1,
                "openglSupport": 0,
                "TextureFormats": ASTC,
                "isVariableMaxFPS": 1,
                "unityQualityGraphics": 1,
                "CpuAffinityBitmask": 0x00000000000001FE,
                "GpuUsageNodeMask": 00000001h,
                "JobsUtility.JobWorkerMaximumCount": 8
            }
        ]
    }' > /sdcard/Android/data/com.miHoYo.GenshinImpact/files/hardware_model_config.json
    echo "Success!"
else
    echo "Failed!"
    echo "File not found or Game not installed properly"
fi
sleep 3