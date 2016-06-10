LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := ahbottomnavigation
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += com.android.gallery3d.common2
LOCAL_STATIC_JAVA_LIBRARIES += mp4parser
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(call all-renderscript-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_RESOURCE_DIR += \
    $(LOCAL_PATH)/res \
    $(TOP)/external/ahbottomnavigation/ahbottomnavigation/src/main/res \
    $(TOP)/frameworks/support/v7/appcompat/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.aurelhubert.ahbottomnavigation \
    --extra-packages android.support.v7.appcompat

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

LOCAL_PROGUARD_ENABLED := disabled

#LOCAL_SDK_VERSION := current

LOCAL_JNI_SHARED_LIBRARIES := libjni_eglfence libjni_filtershow_filters libjni_jpegstream
LOCAL_SHARED_LIBRARIES += libjni_dualcamera

ifeq ($(TARGET_TS_MAKEUP), true)
  LOCAL_REQUIRED_MODULES := libts_detected_face_jni libts_face_beautify_jni
endif

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_MULTILIB := 32

LOCAL_JAVA_LIBRARIES += org.apache.http.legacy

include $(BUILD_PACKAGE)

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)

# Use the following include to make gallery test apk
include $(call all-makefiles-under, $(LOCAL_PATH))

endif
