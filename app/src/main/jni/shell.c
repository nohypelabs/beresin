#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "ShellEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_OUTPUT (2 * 1024 * 1024)  // 2MB max output buffer

/**
 * Execute a shell command and return stdout+stderr as a Java string.
 * Uses popen() which is simple and reliable for one-shot commands.
 */
JNIEXPORT jstring JNICALL
Java_com_aicleaner_engine_ShellEngine_nativeExec(
    JNIEnv *env,
    jobject thiz,
    jstring jcmd
) {
    if (jcmd == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: null command");
    }

    const char *cmd = (*env)->GetStringUTFChars(env, jcmd, NULL);
    if (cmd == NULL) {
        return (*env)->NewStringUTF(env, "ERROR: GetStringUTFChars failed");
    }

    LOGI("Executing: %s", cmd);

    // Build full command with stderr redirect
    char full_cmd[8192];
    snprintf(full_cmd, sizeof(full_cmd), "%s 2>&1", cmd);

    // Execute
    FILE *fp = popen(full_cmd, "r");
    if (fp == NULL) {
        (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        LOGE("popen failed for: %s", cmd);
        return (*env)->NewStringUTF(env, "ERROR: popen failed");
    }

    // Read output
    char *output = (char *)malloc(MAX_OUTPUT);
    if (output == NULL) {
        pclose(fp);
        (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        return (*env)->NewStringUTF(env, "ERROR: malloc failed");
    }

    size_t total = 0;
    char line[4096];

    while (fgets(line, sizeof(line), fp) != NULL) {
        size_t len = strlen(line);
        if (total + len < MAX_OUTPUT - 1) {
            memcpy(output + total, line, len);
            total += len;
        } else {
            // Buffer full, truncate
            break;
        }
    }

    output[total] = '\0';

    int status = pclose(fp);
    LOGI("Command exited with status: %d, output length: %zu", status, total);

    // Create Java string
    jstring result = (*env)->NewStringUTF(env, output);

    // Cleanup
    free(output);
    (*env)->ReleaseStringUTFChars(env, jcmd, cmd);

    return result;
}

/**
 * Execute command with PRoot wrapper.
 * prootPath: path to proot binary
 * rootfsPath: path to ubuntu rootfs
 * cmd: command to execute inside PRoot
 */
JNIEXPORT jstring JNICALL
Java_com_aicleaner_engine_ShellEngine_nativeExecProot(
    JNIEnv *env,
    jobject thiz,
    jstring jproot,
    jstring jrootfs,
    jstring jcmd
) {
    const char *proot = (*env)->GetStringUTFChars(env, jproot, NULL);
    const char *rootfs = (*env)->GetStringUTFChars(env, jrootfs, NULL);
    const char *cmd = (*env)->GetStringUTFChars(env, jcmd, NULL);

    char full_cmd[8192];
    snprintf(full_cmd, sizeof(full_cmd),
        "PROOT_NO_SECCOMP=1 %s "
        "--rootfs=%s "
        "--link2symlink "
        "--cwd=/root "
        "/bin/bash -c '%s' 2>&1",
        proot, rootfs, cmd
    );

    LOGI("Proot exec: %s", cmd);

    FILE *fp = popen(full_cmd, "r");
    if (fp == NULL) {
        (*env)->ReleaseStringUTFChars(env, jproot, proot);
        (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
        (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        return (*env)->NewStringUTF(env, "ERROR: proot popen failed");
    }

    char *output = (char *)malloc(MAX_OUTPUT);
    size_t total = 0;
    char line[4096];

    while (fgets(line, sizeof(line), fp) != NULL) {
        size_t len = strlen(line);
        if (total + len < MAX_OUTPUT - 1) {
            memcpy(output + total, line, len);
            total += len;
        }
    }
    output[total] = '\0';

    pclose(fp);

    jstring result = (*env)->NewStringUTF(env, output);

    free(output);
    (*env)->ReleaseStringUTFChars(env, jproot, proot);
    (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
    (*env)->ReleaseStringUTFChars(env, jcmd, cmd);

    return result;
}

/**
 * Check if a file exists and is executable.
 */
JNIEXPORT jboolean JNICALL
Java_com_aicleaner_engine_ShellEngine_nativeFileExists(
    JNIEnv *env,
    jobject thiz,
    jstring jpath
) {
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    // Use access() to check existence + execute permission
    char cmd[4096];
    snprintf(cmd, sizeof(cmd), "test -x %s", path);

    int result = system(cmd);
    jboolean exists = (result == 0);

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return exists;
}
