LOCAL_PATH 	:= $(call my-dir)
PREBUILT_ROOT   := ../deps/$(TARGET_ARCH_ABI)/root

include $(CLEAR_VARS)
LOCAL_MODULE            := celt
LOCAL_SRC_FILES         := $(PREBUILT_ROOT)/lib/libcelt051.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(PREBUILT_ROOT)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
# Move to GSTREAMER_EXTRA_DEPS in gst 1.5
LOCAL_MODULE            := jpeg
LOCAL_SRC_FILES         := $(PREBUILT_ROOT)/lib/libjpeg.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(PREBUILT_ROOT)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libcrypto
LOCAL_SRC_FILES         := $(PREBUILT_ROOT)/lib/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(PREBUILT_ROOT)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libssl
LOCAL_SRC_FILES         := $(PREBUILT_ROOT)/lib/libssl.a
LOCAL_STATIC_LIBRARIES  := libcrypto
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(PREBUILT_ROOT)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := spice-client-glib
LOCAL_SRC_FILES         := $(PREBUILT_ROOT)/lib/libspice-client-glib-2.0.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/$(PREBUILT_ROOT)/include/spice-client-glib-2.0 \
                           $(LOCAL_PATH)/$(PREBUILT_ROOT)/include/spice-1
LOCAL_SHARED_LIBRARIES  := gstreamer_android
LOCAL_STATIC_LIBRARIES  := celt jpeg libssl
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
GSTREAMER_ROOT            := $(LOCAL_PATH)/../deps/$(TARGET_ARCH_ABI)/gstreamer
GSTREAMER_NDK_BUILD_PATH  := $(GSTREAMER_ROOT)/share/gst-android/ndk-build/
include $(GSTREAMER_NDK_BUILD_PATH)/plugins.mk
GSTREAMER_PLUGINS         := $(GSTREAMER_PLUGINS_CORE) $(GSTREAMER_PLUGINS_SYS)
G_IO_MODULES              := gnutls
GSTREAMER_EXTRA_DEPS      := pixman-1 gstreamer-app-1.0 libsoup-2.4 libxml-2.0 glib-2.0 gthread-2.0 gobject-2.0 opus
include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk


include $(CLEAR_VARS)
LOCAL_MODULE    := spice

LOCAL_SRC_FILES := android/android-io.c \
                   android/android-service.c \
                   android/android-socket.c \
                   android/android-spice-widget.c \
                   android/android-spicy.c

LOCAL_LDLIBS 	+= -ljnigraphics -llog

LOCAL_CPPFLAGS  += -DG_LOG_DOMAIN=\"android-spice\"

LOCAL_CFLAGS 	:=  $(LOCAL_CPPFLAGS) \
                   -std=gnu99 -Wall -Wno-sign-compare -Wno-deprecated-declarations -Wno-int-to-pointer-cast -Wno-pointer-to-int-cast -Wl,--no-undefined \
                   -fPIC -DPIC -O3 -funroll-loops

LOCAL_EXPORT_CFLAGS += $(LOCAL_CFLAGS)
LOCAL_EXPORT_LDLIBS += $(LOCAL_LDLIBS)
LOCAL_ARM_MODE := arm
LOCAL_SHARED_LIBRARIES := gstreamer_android
LOCAL_STATIC_LIBRARIES := spice-client-glib
include $(BUILD_SHARED_LIBRARY)
