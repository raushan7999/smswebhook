# 14 · Website

> **Purpose:** describe the product website, how it is built and hosted, and how to keep it accurate.

## 1. Summary

| Item | Value |
|---|---|
| URL | https://smswebhook.bestcallerapp.com/ |
| Source | `website/` (plain HTML, CSS and a small script; no build step to serve it) |
| Generator | `release_item/tools/make_website.py` (regenerates pages from shared templates) |
| Hosting | GitHub Pages (recommended), or any static host |
| Design | Design system shared with bestgpsmapcamera.bestcallerapp.com, brand green `#15803D` (AA contrast) from the icon; Inter (self-hosted, OFL); light/dark themes; no trackers |

> **Status (2026-10-10):** generated and checked (17 pages, 583 internal links verified, desktop and mobile
> renders reviewed). **Not yet published**: hosting and DNS (§3) are manual steps.

## 2. Pages

| Path | Purpose | Referenced by |
|---|---|---|
| `/` | Product page: hero, features, how it works, screens, video, privacy, FAQ, download | Play listing **Website**, marketing |
| `/guide/` | Tutorial index | App → Settings → **How-to guide** |
| `/guide/<topic>/` | Step-by-step tutorials (getting started, adding a webhook, filters, payloads, extraction, delivery and retries, history and logs, settings, receiving webhooks on your server, troubleshooting) | Guide index |
| `/privacy/` | Privacy policy | Play Console, app **Privacy policy** |
| `/terms/` | Terms of use | App **Terms of use** |
| `/support/` (`#delete`) | Contact and data deletion | Play listing, app **Contact support** |
| `/404.html`, `/robots.txt`, `/sitemap.xml`, `/CNAME`, `/.nojekyll` | Hosting files | GitHub Pages, search engines |

## 3. Hosting and DNS

1. Create a repository (for example `smswebhook-website`) and push the contents of `website/` to `main`.
2. Repository → Settings → Pages → Deploy from branch → `main` / root.
3. The `CNAME` file contains `smswebhook.bestcallerapp.com`.
4. DNS for `bestcallerapp.com`: add `CNAME smswebhook → <github-user>.github.io` (proxy/CDN off while the
   certificate is issued).
5. After DNS resolves, enable **Enforce HTTPS** in the Pages settings.

## 4. Maintenance rules

- The privacy policy MUST match the app's real data flows ([11](11-security-and-privacy.md)) and the Play Data
  safety form. Update its **Last updated** date on every change.
- Screenshots on the site come from `release_item/4_graphics/screenshots_v<ver>/` via the generator; never use
  real messages or real endpoints.
- Google Play links point to `https://play.google.com/store/apps/details?id=com.bestcallerapp.smswebhook`.
  They return 404 until the app is published.
- Check the site at 360 px width and in dark mode after changes.
