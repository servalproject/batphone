# Included by top-level Android.mk

include $(CLEAR_VARS)

LOCAL_MODULE:= sqlite3

LOCAL_SRC_FILES:= \
		sqlite3/encode.c \
		sqlite3/sqlite3.c

include $(BUILD_STATIC_LIBRARY)
