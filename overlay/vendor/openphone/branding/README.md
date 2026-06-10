OpenPhone Branding
==================

This directory keeps source branding assets used by the OpenPhone product layer.

- `wallpapers/background-mobile.jpg` is the source image for the framework
  default wallpaper overlay at
  `vendor/openphone/overlay/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png`.
- `logos/openphone-logo.svg` is the source logo used to generate
  `vendor/openphone/media/bootanimation.zip`.

The Pixel bootloader Google splash appears before Android starts and is not
controlled by these Android product resources. The boot animation starts after
firmware and verified boot hand off to Android.
