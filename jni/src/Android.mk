LOCAL_PATH 	:= $(call my-dir)

include $(CLEAR_VARS)

LIB_PATH := $(LOCAL_PATH)/../../libs/armeabi

SPICE_CLIENT_ANDROID_DEPS   := $(LOCAL_PATH)/../libs/deps

CROSS_DIR  := /opt/gstreamer
spice_objs := \
    $(SPICE_CLIENT_ANDROID_DEPS)/lib/libssl.a \
    $(SPICE_CLIENT_ANDROID_DEPS)/lib/libcrypto.a \
    $(SPICE_CLIENT_ANDROID_DEPS)/lib/libcelt051.a \
    $(SPICE_CLIENT_ANDROID_DEPS)/lib/libjpeg.a

LOCAL_MODULE    := spice

LOCAL_SRC_FILES := android/android-io.c \
                   android/android-service.c \
                   android/android-socket.c \
                   android/android-spice-widget.c \
                   android/android-spicy.c \
                   gtk/bio-gio.c \
                   gtk/channel-base.c \
                   gtk/channel-cursor.c \
                   gtk/channel-display.c \
                   gtk/channel-display-mjpeg.c \
                   gtk/channel-inputs.c \
                   gtk/channel-main.c \
                   gtk/channel-playback.c \
                   gtk/channel-port.c \
                   gtk/channel-record.c \
                   gtk/client_sw_canvas.c \
                   gtk/coroutine_gthread.c \
                   gtk/decode-glz.c \
                   gtk/decode-jpeg.c \
                   gtk/decode-zlib.c \
                   gtk/gio-coroutine.c \
                   gtk/glib-compat.c \
                   gtk/spice-audio.c \
                   gtk/spice-channel.c \
                   gtk/spice-client.c \
                   gtk/spice-cmdline.c \
                   gtk/spice-glib-enums.c \
                   gtk/spice-gstaudio.c \
                   gtk/spice-marshal.c \
                   gtk/spice-session.c \
                   gtk/spice-uri.c \
                   gtk/spice-util.c \
                   gtk/usb-device-manager.c \
                   gtk/wocky-http-proxy.c \
                   spice-common/common/backtrace.c \
                   spice-common/common/canvas_utils.c \
                   spice-common/common/generated_client_demarshallers1.c \
                   spice-common/common/generated_client_demarshallers.c \
                   spice-common/common/generated_client_marshallers1.c \
                   spice-common/common/generated_client_marshallers.c \
                   spice-common/common/lines.c \
                   spice-common/common/log.c \
                   spice-common/common/lz.c \
                   spice-common/common/marshaller.c \
                   spice-common/common/mem.c \
                   spice-common/common/pixman_utils.c \
                   spice-common/common/quic.c \
                   spice-common/common/region.c \
                   spice-common/common/rop3.c \
                   spice-common/common/snd_codec.c \
                   spice-common/common/ssl_verify.c

LOCAL_LDLIBS 	+= $(spice_objs) \
                   -ljnigraphics -llog -ldl -lstdc++ -lz \
                   -malign-double -malign-loops

LOCAL_CPPFLAGS 	+= -DG_LOG_DOMAIN=\"GSpice\" \
                   -DSPICE_GTK_LOCALEDIR=\"/usr/local/share/locale\" \
                   -DHAVE_CONFIG_H -UHAVE_SYS_SHM_H \
                   -D_REENTRANT -DWITH_GSTAUDIO

LOCAL_C_INCLUDES += \
                    $(LOCAL_PATH)/gtk \
                    $(LOCAL_PATH)/spice-common \
                    $(LOCAL_PATH)/spice-common/common \
                    $(LOCAL_PATH)/spice-common/spice-protocol \
                    $(LOCAL_PATH)/virt-viewer \
                    $(SPICE_CLIENT_ANDROID_DEPS)/include \
                    $(SPICE_CLIENT_ANDROID_DEPS)/include/jpeg-turbo \
                    $(CROSS_DIR)/include \
                    $(CROSS_DIR)/include/glib-2.0 \
                    $(CROSS_DIR)/include/libxml2 \
                    $(CROSS_DIR)/include/pixman-1 \
                    $(CROSS_DIR)/include/spice-1 \
                    $(CROSS_DIR)/lib/glib-2.0/include

LOCAL_CFLAGS 	:=  $(LOCAL_CPPFLAGS) \
                   -std=gnu99 -Wall -Wno-sign-compare -Wno-deprecated-declarations -Wno-int-to-pointer-cast -Wno-pointer-to-int-cast -Wl,--no-undefined \
                   -fPIC -DPIC -O3 -funroll-loops -ffast-math

LOCAL_EXPORT_CFLAGS += $(LOCAL_CFLAGS)
LOCAL_EXPORT_LDLIBS += $(LOCAL_LDLIBS)
LOCAL_ARM_MODE := arm
LOCAL_SHARED_LIBRARIES := gstreamer_android
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
GSTREAMER_SDK_ROOT_ANDROID := /opt/gstreamer
ifndef GSTREAMER_SDK_ROOT
ifndef GSTREAMER_SDK_ROOT_ANDROID
$(error GSTREAMER_SDK_ROOT_ANDROID is not defined!)
endif
GSTREAMER_SDK_ROOT        := $(GSTREAMER_SDK_ROOT_ANDROID)
endif
GSTREAMER_NDK_BUILD_PATH  := $(GSTREAMER_SDK_ROOT)/share/gst-android/ndk-build/
include $(GSTREAMER_NDK_BUILD_PATH)/plugins.mk
GSTREAMER_PLUGINS         := $(GSTREAMER_PLUGINS_CORE) $(GSTREAMER_PLUGINS_SYS)
G_IO_MODULES              := gnutls
GSTREAMER_EXTRA_DEPS      := pixman-1 gstreamer-app-0.10 libsoup-2.4 libxml-2.0 gthread-2.0 gobject-2.0

include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer.mk
