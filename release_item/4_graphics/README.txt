GRAPHICS — SMS Webhook 1.0.0
============================
app-icon-512.png               Play app icon, 512×512 32-bit PNG. Message bubble + webhook symbol on brand green #16A34A
                               (artwork from the BC SMS Webhook app; sources in source/).
feature-graphic-1024x500.png   Play feature graphic, 1024×500, 24-bit PNG (no alpha)
store/phone_v1.0.0/            8 captioned phone screenshots, 1080×1920 — upload in file order
store/tablet-7in_v1.0.0/       8 × 1200×1920
store/tablet-10in_v1.0.0/      8 × 1600×2560
screenshots_v1.0.0/            17 raw screenshots (Samsung Galaxy A17, 1080×2340) used by store images, video and website
source/                        icon layers: background.svg, foreground.svg, monochrome.svg, icon-full.svg (store icon)

How the raw screenshots were made (so the next version matches)
  * Debug build with fictional demo data:
      adb shell am start -n com.bestcallerapp.smswebhook.debug/com.bestcallerapp.smswebhook.presentation.MainActivity
      adb shell am broadcast -a com.bestcallerapp.smswebhook.DEMO_SEED \
          -n com.bestcallerapp.smswebhook.debug/com.bestcallerapp.smswebhook.demo.DemoDataReceiver
    (example.com webhooks, sample bank/OTP texts — never real messages or endpoints).
  * Battery optimisation exempted temporarily so Home shows no battery warning:
      adb shell dumpsys deviceidle whitelist +com.bestcallerapp.smswebhook.debug     (remove afterwards with -)
  * Check each shot for notification pop-ups (e.g. Truecaller) before use.
  * Status bar (100 px) and navigation bar (135 px) are cropped by tools/brand.py.

Regenerate:  cd ../tools && python3 make_store_images.py v1.0.0 && python3 make_feature_graphic.py v1.0.0
