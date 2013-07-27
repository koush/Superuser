# Root AOSP source makefile
# su is built here, and 

my_path := $(call my-dir)
LOCAL_PATH := $(my_path)

ifdef SUPERUSER_EMBEDDED
SUPERUSER_PACKAGE := com.android.settings

# make sure init.superuser.rc is imported from
# init.rc or similar

include $(CLEAR_VARS)
LOCAL_MODULE := init.superuser.rc
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ROOT
LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT)/
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

else

ifeq ($(SUPERUSER_PACKAGE),)
SUPERUSER_PACKAGE := com.thirdparty.superuser
endif
include $(my_path)/Superuser/Android.mk

endif


include $(CLEAR_VARS)

LOCAL_MODULE := su
ifdef SUPERUSER_EMBEDDED
$(LOCAL_MODULE): $(TARGET_ROOT_OUT)/init.superuser.rc
endif

LOCAL_MODULE_TAGS := eng debug
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc
LOCAL_C_INCLUDES := external/sqlite/dist
LOCAL_SRC_FILES := Superuser/jni/su/su.c Superuser/jni/su/daemon.c Superuser/jni/su/activity.c Superuser/jni/su/db.c Superuser/jni/su/utils.c ../../sqlite/dist/sqlite3.c
LOCAL_CFLAGS := -DSQLITE_OMIT_LOAD_EXTENSION -DREQUESTOR=\"$(SUPERUSER_PACKAGE)\"

ifdef SUPERUSER_PACKAGE_PREFIX
  LOCAL_CFLAGS += -DREQUESTOR_PREFIX=\"$(SUPERUSER_PACKAGE_PREFIX)\"
endif

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_EXECUTABLE)


SYMLINKS := $(addprefix $(TARGET_OUT)/bin/,su)
$(SYMLINKS): $(LOCAL_MODULE)
$(SYMLINKS): $(LOCAL_INSTALLED_MODULE) $(LOCAL_PATH)/Android.mk
	@echo "Symlink: $@ -> /system/xbin/su"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf /system/xbin/su $@

ALL_DEFAULT_INSTALLED_MODULES += $(SYMLINKS)

# We need this so that the installed files could be picked up based on the
# local module name
ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(SYMLINKS)
