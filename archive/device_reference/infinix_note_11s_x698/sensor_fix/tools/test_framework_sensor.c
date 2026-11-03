#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/sensor.h>

int main() {
    printf("[*] Acquiring ASensorManager...\n");
    ASensorManager* sm = ASensorManager_getInstanceForPackage("com.test.sensor");
    if (!sm) {
        printf("[-] Failed to get ASensorManager\n");
        return 1;
    }

    ASensorList list;
    int count = ASensorManager_getSensorList(sm, &list);
    printf("[+] Total sensors available: %d\n", count);

    const ASensor* acc = ASensorManager_getDefaultSensor(sm, ASENSOR_TYPE_ACCELEROMETER);
    if (!acc) {
        printf("[-] No default accelerometer found!\n");
    } else {
        printf("[+] Accel: %s, vendor: %s\n", ASensor_getName(acc), ASensor_getVendor(acc));
    }

    const ASensor* gyro = ASensorManager_getDefaultSensor(sm, ASENSOR_TYPE_GYROSCOPE);
    if (!gyro) {
        printf("[-] No default gyro found!\n");
    } else {
        printf("[+] Gyro: %s, vendor: %s\n", ASensor_getName(gyro), ASensor_getVendor(gyro));
    }

    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    ASensorEventQueue* queue = ASensorManager_createEventQueue(sm, looper, 1, NULL, NULL);
    if (!queue) {
        printf("[-] Failed to create event queue\n");
        return 1;
    }

    if (acc) {
        int ret = ASensorEventQueue_enableSensor(queue, acc);
        printf("[*] Enable accel ret: %d\n", ret);
        ret = ASensorEventQueue_setEventRate(queue, acc, 20000); // 50Hz
        printf("[*] SetRate accel ret: %d\n", ret);
    }

    printf("[*] Polling 10 events (5s timeout)...\n");
    int received = 0;
    for (int i = 0; i < 50; i++) {
        int ident = ALooper_pollOnce(100, NULL, NULL, NULL);
        if (ident == 1) {
            ASensorEvent event;
            while (ASensorEventQueue_getEvents(queue, &event, 1) > 0) {
                received++;
                if (event.type == ASENSOR_TYPE_ACCELEROMETER) {
                    printf("[#%02d] ACC: x=%.2f, y=%.2f, z=%.2f (ts=%lld)\n",
                           received, event.acceleration.x, event.acceleration.y,
                           event.acceleration.z, (long long)event.timestamp);
                }
                if (received >= 10) goto done;
            }
        }
    }
done:
    if (received == 0) {
        printf("[-] TIMEOUT: Received 0 sensor events!\n");
    } else {
        printf("[+] SUCCESS: Received %d sensor events!\n", received);
    }

    if (acc) ASensorEventQueue_disableSensor(queue, acc);
    ASensorManager_destroyEventQueue(sm, queue);
    return (received > 0) ? 0 : 2;
}
