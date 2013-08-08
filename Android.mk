# Root AOSP source makefile
# su is built here, and 

my_path := $(call my-dir)

ifdef SUPERUSER_EMBEDDED
SUPERUSER_PACKAGE := com.android.settings
else
ifeq ($(SUPERUSER_PACKAGE),)
SUPERUSER_PACKAGE := com.thirdparty.superuser
endif
include $(my_path)/Superuser/Android.mk
endif


LOCAL_PATH := $(my_path)
include $(CLEAR_VARS)

LOCAL_MODULE := su
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

ifdef SUPERUSER_EMBEDDED

# make sure init.superuser.rc is imported from
# init.rc or similar

SUPERUSER_RC := $(TARGET_ROOT_OUT)/init.superuser.rc
$(SUPERUSER_RC): $(LOCAL_MODULE)
$(SUPERUSER_RC): $(LOCAL_INSTALLED_MODULE) $(LOCAL_PATH)/Android.mk
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) cp external/koush/Superuser/init.superuser.rc $@


SUPERUSER_MARKER := $(TARGET_OUT_ETC)/.has_su_daemon
$(SUPERUSER_MARKER): $(LOCAL_MODULE)
$(SUPERUSER_MARKER): $(LOCAL_INSTALLED_MODULE) $(LOCAL_PATH)/Android.mk
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) touch $@

ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(TARGET_ROOT_OUT)/init.superuser.rc $(TARGET_OUT_ETC)/.has_su_daemon

endif
