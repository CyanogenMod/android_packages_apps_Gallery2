LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += com.android.gallery3d.common2 

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)
ifeq ($(BOARD_USES_LEGACYCAMERA), true)
LOCAL_SRC_FILES += $(call all-java-files-under, ../LegacyCamera/src)
else
LOCAL_SRC_FILES += $(call all-java-files-under, ../Camera/src)
endif

ifeq ($(BOARD_USES_LEGACYCAMERA), true)
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res packages/apps/LegacyCamera/res
else
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res packages/apps/Camera/res
endif
LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages com.android.camera

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

#LOCAL_SDK_VERSION := current

ifeq ($(BOARD_USES_LEGACYCAMERA), true)
LOCAL_JNI_SHARED_LIBRARIES := libjni_legacymosaic libjni_eglfence
else
LOCAL_JNI_SHARED_LIBRARIES := libjni_mosaic libjni_eglfence
endif

ifeq ($(BOARD_USES_LEGACYCAMERA), true)
LOCAL_REQUIRED_MODULES := libjni_legacymosaic libjni_eglfence
else
LOCAL_REQUIRED_MODULES := libjni_mosaic libjni_eglfence
endif

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

ifeq ($(BOARD_USES_LEGACYCAMERA), true)
include $(call all-makefiles-under, packages/apps/LegacyCamera/jni)
else
include $(call all-makefiles-under, jni)
endif

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)
# Use the following include to make gallery test apk.
include $(call all-makefiles-under, $(LOCAL_PATH))

# Use the following include to make camera test apk.
ifeq ($(BOARD_USES_LEGACYCAMERA), true)
include $(call all-makefiles-under, packages/apps/LegacyCamera)
else
include $(call all-makefiles-under, packages/apps/Camera)
endif

endif
