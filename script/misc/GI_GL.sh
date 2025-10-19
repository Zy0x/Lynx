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
            "vulkanFlag": 0,
            "openglSupport": 1,
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
sleep 3