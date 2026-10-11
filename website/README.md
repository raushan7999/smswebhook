# SMS Webhook — website

Static site for the app at **https://smswebhook.bestcallerapp.com/** (generated; do not edit HTML by hand).

| Page | URL | Use |
|---|---|---|
| Home | `/` | Play listing "Website", marketing |
| How-to guide | `/guide/`, `/guide/<topic>/` | 11 tutorials; linked from the app (Settings → How-to guide) |
| Privacy policy | `/privacy/` | Play Console → App content; in-app link |
| Terms of use | `/terms/` | In-app link |
| Support + data deletion | `/support/` (`#delete`) | Play listing; in-app "Contact support" |

**Source:** `release_item/tools/make_website.py` (content and templates) + `release_item/4_graphics/screenshots_<ver>/`.
Regenerate with `python3 release_item/tools/make_website.py v1.0.0`, review, then publish this folder.

**Hosting:** GitHub Pages (or any static host). `CNAME` holds the custom domain; DNS: `CNAME smswebhook → <user>.github.io`.
No build step, no trackers, fonts self-hosted (Inter, OFL).
