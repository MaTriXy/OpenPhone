# OpenPhone emulator product for x86_64 hosts.
#
# This target layers OpenPhone onto LineageOS's SDK phone image so developers
# can build and boot OpenPhone in the Android emulator without a physical phone.

# Lineage/AOSP product conditionals use LINEAGE_BUILD to avoid installing
# duplicate AOSP fallback assets such as the sample APN list.
LINEAGE_BUILD := sdk_phone_x86_64

$(call inherit-product, vendor/lineage/build/target/product/lineage_sdk_phone_x86_64.mk)
$(call inherit-product, vendor/openphone/products/openphone_common.mk)

PRODUCT_NAME := openphone_sdk_phone_x86_64
PRODUCT_MODEL := OpenPhone SDK Phone x86_64
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
