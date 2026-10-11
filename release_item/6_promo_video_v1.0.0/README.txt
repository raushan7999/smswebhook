PROMO / TEASER VIDEOS — SMS Webhook 1.0.0
=========================================
promo-landscape-1920x1080.mp4   39.2 s, H.264 30 fps + AAC stereo 192 kbps → Play promo video (via YouTube), website
teaser-vertical-1080x1920.mp4   39.2 s, same → YouTube Shorts, Instagram Reels, WhatsApp Status
soundtrack.wav                  the soundtrack alone (44.1 kHz stereo); videos are normalised to −14 LUFS
_silent_*.mp4                   the same videos without sound (masters for your own music)
youtube-thumbnail-1280x720.png  custom thumbnail for the YouTube upload
teaser-poster-1080x1920.png     still for social posts

Story (10 scenes, built from real 1.0.0 screens): intro → SMS arrives, app forwards JSON, "HTTP 200 · Delivered"
→ multiple webhooks → smart filters → payload formats → regex extraction → every delivery tracked → reliable by
design (offline, retries, no duplicates, logs) → private by design → end card (website, Free · No ads).
The end card shows the website, not "Get it on Google Play", because the Play listing is not live yet.

Play Store promo video
  1. Upload promo-landscape-1920x1080.mp4 to YouTube: Public or Unlisted (NOT private), monetisation OFF,
     "Made for kids": No. Use youtube-thumbnail-1280x720.png.
  2. Paste the URL in Play Console → Main store listing → Video.

Sound: original music and effects synthesised by tools/make_audio.py (pure Python; no samples, no licences, no
Content ID claims). 112 BPM, Am–F–C–G; whoosh on scene changes, message chimes, pops for chips and cards.

Regenerate (after new screenshots or captions):
  cd ../tools
  python3 make_teaser.py v1.0.0 vertical && python3 make_teaser.py v1.0.0 landscape
  python3 make_audio.py v1.0.0
  python3 make_youtube_thumbnail.py v1.0.0
