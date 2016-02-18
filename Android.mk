LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += com.android.gallery3d.common2
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit
LOCAL_STATIC_JAVA_LIBRARIES += mp4parser
#LOCAL_STATIC_JAVA_LIBRARIES += android-support-v8-renderscript

#LOCAL_RENDERSCRIPT_TARGET_API := 18
#LOCAL_RENDERSCRIPT_COMPATIBILITY := 18
#LOCAL_RENDERSCRIPT_FLAGS := -rs-package-name=android.support.v8.renderscript

# Keep track of previously compiled RS files too (from bundled GalleryGoogle).
prev_compiled_rs_files := $(call all-renderscript-files-under, src)


# We already have these files from GalleryGoogle, so don't install them.
LOCAL_RENDERSCRIPT_SKIP_INSTALL := $(prev_compiled_rs_files)
# $(warning Anand Commend LOCAL_RENDERSCRIPT_SKIP_INSTALL is $(LOCAL_RENDERSCRIPT_SKIP_INSTALL))

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(prev_compiled_rs_files)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)

# $(warning Anand Commend LOCAL_RENDERSCRIPT_SKIP_INSTALL is $(LOCAL_RENDERSCRIPT_SKIP_INSTALL))

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res

LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

LOCAL_PROGUARD_ENABLED := disabled

#LOCAL_SDK_VERSION := current

LOCAL_JNI_SHARED_LIBRARIES := libjni_eglfence libjni_filtershow_filters libjni_jpegstream
LOCAL_SHARED_LIBRARIES += libjni_dualcamera

LOCAL_REQUIRED_MODULES := libts_detected_face_jni libts_face_beautify_jni

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_MULTILIB := 32

LOCAL_JAVA_LIBRARIES += org.apache.http.legacy

include $(BUILD_PACKAGE)

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)

# Use the following include to make gallery test apk
include $(call all-makefiles-under, $(LOCAL_PATH))

endif
