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
LOCAL_SRC_FILES := executables/su/su.c executables/su/daemon.c executables/su/activity.c executables/su/db.c executables/su/utils.c executables/su/pts.c executables/sqlite3/sqlite3.c
LOCAL_CFLAGS := -DSQLITE_OMIT_LOAD_EXTENSION -DREQUESTOR=\"$(SUPERUSER_PACKAGE)\"

ifdef SUPERUSER_PACKAGE_PREFIX
  LOCAL_CFLAGS += -DREQUESTOR_PREFIX=\"$(SUPERUSER_PACKAGE_PREFIX)\"
endif

ifdef SUPERUSER_EMBEDDED
  LOCAL_CFLAGS += -DSUPERUSER_EMBEDDED
endif

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_MODULE := placeholder
LOCAL_MODULE_TAGS := eng debug optional
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libcutils libselinux libsepol
LOCAL_SRC_FILES := executables/placeholder/placeholder.c executables/placeholder/policy.c
LOCAL_C_INCLUDES := external/libsepol/include
LOCAL_CFLAGS := -std=gnu99

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
include $(BUILD_EXECUTABLE)


SYMLINKS := $(addprefix $(TARGET_OUT)/bin/,su)
$(SYMLINKS):
	@echo "Symlink: $@ -> /system/xbin/su"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf ../xbin/su $@

ALL_DEFAULT_INSTALLED_MODULES += $(SYMLINKS)

# We need this so that the installed files could be picked up based on the
# local module name
ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(SYMLINKS)

ifdef SUPERUSER_EMBEDDED

# make sure init.superuser.rc is imported from
# init.rc or similar

SUPERUSER_RC := $(TARGET_ROOT_OUT)/init.superuser.rc
$(SUPERUSER_RC): $(LOCAL_PATH)/init.superuser.rc | $(ACP)
	$(copy-file-to-new-target)

SUPERUSER_MARKER := $(TARGET_OUT_ETC)/.has_su_daemon
$(SUPERUSER_MARKER): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) touch $@

ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(SUPERUSER_RC) $(SUPERUSER_MARKER)

endif
