# Root AOSP source makefile
# su is built here, and 

my_path := $(call my-dir)

LOCAL_PATH := $(my_path)
include $(CLEAR_VARS)

LOCAL_MODULE := su
LOCAL_MODULE_TAGS := eng debug optional
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libcutils libselinux
LOCAL_C_INCLUDES := external/sqlite/dist
LOCAL_SRC_FILES := Superuser/jni/su/su.c Superuser/jni/su/daemon.c Superuser/jni/su/activity.c Superuser/jni/su/db.c Superuser/jni/su/utils.c Superuser/jni/su/pts.c Superuser/jni/sqlite3/sqlite3.c
LOCAL_CFLAGS := -DSQLITE_OMIT_LOAD_EXTENSION -DREQUESTOR=\"$(SUPERUSER_PACKAGE)\"

ifdef SUPERUSER_PACKAGE_PREFIX
  LOCAL_CFLAGS += -DREQUESTOR_PREFIX=\"$(SUPERUSER_PACKAGE_PREFIX)\"
endif

ifdef SUPERUSER_EMBEDDED
  LOCAL_CFLAGS += -DSUPERUSER_EMBEDDED
endif

LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT_SBIN)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

ifdef SUPERUSER_EMBEDDED

SUPERUSER_MARKER := $(TARGET_OUT_ETC)/.has_su_daemon
$(SUPERUSER_MARKER): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) touch $@

ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(SUPERUSER_RC) $(SUPERUSER_MARKER)

endif
