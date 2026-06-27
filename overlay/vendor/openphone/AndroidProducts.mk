PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/products/openphone_arm64.mk \
    $(LOCAL_DIR)/products/openphone_sdk_phone_arm64.mk \
    $(LOCAL_DIR)/products/openphone_sdk_phone_x86_64.mk \
    $(LOCAL_DIR)/products/openphone_tegu_smoke.mk \
    $(LOCAL_DIR)/products/openphone_tegu.mk

COMMON_LUNCH_CHOICES := \
    openphone_arm64-eng \
    openphone_arm64-bp4a-eng \
    openphone_arm64-bp4a-userdebug \
    openphone_sdk_phone_arm64-eng \
    openphone_sdk_phone_arm64-bp4a-eng \
    openphone_sdk_phone_arm64-bp4a-userdebug \
    openphone_sdk_phone_x86_64-eng \
    openphone_sdk_phone_x86_64-bp4a-eng \
    openphone_sdk_phone_x86_64-bp4a-userdebug \
    openphone_tegu_smoke-bp4a-userdebug \
    openphone_tegu-bp4a-userdebug
