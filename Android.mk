# Root AOSP source makefile
# su is built here, and 

LOCAL_PATH := $(call my-dir)

ifdef SUPERUSER_EMBEDDED
SUPERUSER_PACKAGE := com.android.settings
else
ifeq ($(SUPERUSER_PACKAGE),)
SUPERUSER_PACKAGE := com.thirdparty.superuser
endif
include $(LOCAL_PATH)/Superuser/Android.mk
endif

include $(CLEAR_VARS)


LOCAL_MODULE := su
LOCAL_MODULE_TAGS := eng debug
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc
LOCAL_C_INCLUDES := external/sqlite/dist
LOCAL_SRC_FILES := Superuser/jni/su/su.c Superuser/jni/su/activity.c Superuser/jni/su/db.c Superuser/jni/su/utils.c ../../sqlite/dist/sqlite3.c
LOCAL_CFLAGS := -DSQLITE_OMIT_LOAD_EXTENSION -DREQUESTOR=\"$(SUPERUSER_PACKAGE)\"
LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_EXECUTABLE)

