#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
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
 * Escape single quotes in a string for safe use inside bash single-quoted strings.
 * Replaces ' with '\'' (end quote, escaped quote, start quote).
 * Returns a newly allocated string that must be freed by the caller.
 */
static char *escape_single_quotes(const char *input) {
    if (input == NULL) return NULL;

    // Count single quotes
    size_t count = 0;
    for (const char *p = input; *p; p++) {
        if (*p == '\'') count++;
    }

    // Each ' becomes '\'' (4 chars instead of 1)
    size_t len = strlen(input);
    size_t new_len = len + count * 3 + 1;
    char *escaped = (char *)malloc(new_len);
    if (escaped == NULL) return NULL;

    char *out = escaped;
    for (const char *p = input; *p; p++) {
        if (*p == '\'') {
            memcpy(out, "'\\''", 4);
            out += 4;
        } else {
            *out++ = *p;
        }
    }
    *out = '\0';
    return escaped;
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

    // NULL checks for all three strings
    if (proot == NULL || rootfs == NULL || cmd == NULL) {
        if (proot) (*env)->ReleaseStringUTFChars(env, jproot, proot);
        if (rootfs) (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
        if (cmd) (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        return (*env)->NewStringUTF(env, "ERROR: GetStringUTFChars failed");
    }

    // Escape single quotes in the command to prevent injection
    char *escaped_cmd = escape_single_quotes(cmd);
    if (escaped_cmd == NULL) {
        (*env)->ReleaseStringUTFChars(env, jproot, proot);
        (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
        (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        return (*env)->NewStringUTF(env, "ERROR: escape failed");
    }

    char full_cmd[8192];
    snprintf(full_cmd, sizeof(full_cmd),
        "PROOT_NO_SECCOMP=1 %s "
        "--rootfs=%s "
        "--link2symlink "
        "--cwd=/root "
        "/bin/bash -c '%s' 2>&1",
        proot, rootfs, escaped_cmd
    );

    free(escaped_cmd);

    LOGI("Proot exec: %s", cmd);

    FILE *fp = popen(full_cmd, "r");
    if (fp == NULL) {
        (*env)->ReleaseStringUTFChars(env, jproot, proot);
        (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
        (*env)->ReleaseStringUTFChars(env, jcmd, cmd);
        return (*env)->NewStringUTF(env, "ERROR: proot popen failed");
    }

    char *output = (char *)malloc(MAX_OUTPUT);
    if (output == NULL) {
        pclose(fp);
        (*env)->ReleaseStringUTFChars(env, jproot, proot);
        (*env)->ReleaseStringUTFChars(env, jrootfs, rootfs);
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
 * Uses C-level access() instead of system() to prevent command injection.
 */
JNIEXPORT jboolean JNICALL
Java_com_aicleaner_engine_ShellEngine_nativeFileExists(
    JNIEnv *env,
    jobject thiz,
    jstring jpath
) {
    if (jpath == NULL) {
        return JNI_FALSE;
    }

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (path == NULL) {
        return JNI_FALSE;
    }

    // Use access() directly — no shell, no injection risk
    jboolean exists = (access(path, X_OK) == 0) ? JNI_TRUE : JNI_FALSE;

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return exists;
}
