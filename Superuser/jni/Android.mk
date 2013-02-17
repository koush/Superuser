LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
LOCAL_MODULE := su
LOCAL_LDFLAGS := -static
LOCAL_STATIC_LIBRARIES := log
LOCAL_SRC_FILES := su/su.c su/activity.c su/db.c su/utils.c
include $(BUILD_EXECUTABLE)
