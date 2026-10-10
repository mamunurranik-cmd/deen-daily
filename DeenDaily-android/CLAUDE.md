# দ্বীন ডেইলি (Deen Daily) — project notes for Claude

Owner: Anik (Md. Mamunur Rashid), Sylhet, Bangladesh. He writes Banglish (Bengali in Latin letters); **reply in Bengali script**, direct and action-oriented, no lecturing.

## What this is
Bengali-first Islamic daily-tracker app (prayers, quran, zikr, hadith, progress, report, settings). Owner wants a **working app, not a prototype or a spec rewrite**.

## Current state
- `app/src/main/assets/index.html` — the whole app, one file (~180 KB), vanilla JS, data in localStorage (prefix `dd:`). This is the working base.
- `app/src/main/java/com/deendaily/app/MainActivity.java` — Android WebView wrapper that loads the file. Not native Kotlin.
- `.github/workflows/build-apk.yml` — GitHub Actions builds a **debug APK** (JDK 17, Gradle 8.7, AGP 8.5.2, compileSdk 34). No signing key, no Play Store, owner wants zero cost.
- `docs/deen-daily-100page-spec.md` — full spec (put the file here). It targets native Kotlin + Compose; use it as the source of truth for behavior/content, not as an instruction to rewrite natively unless the owner asks.
- The first GitHub build has NOT been verified. If it fails, read the Actions log and fix the cause.

## Locked decisions (do not change silently)
- Asr = **Hanafi (shadow factor 2)**. The spec's §12.1 / §12.3 still say Shafi'i; that is outdated, ignore it.
- No points badge in the header. Header = title + প্রো/বেসিক pill + profile button.
- No invented religious text. Anything unknown is marked **যাচাই দরকার**. Never write Quran/hadith/dua from memory without the owner's verified source.
- Quran section: Arabic + Bengali translation only, **no transliteration**.
- Good-deed points are always kept separate from ibadah %. No divine-reward (sawab) claims, no leaderboards, data stays private, offline-first, history is read-only.
- Teal "liquid glass" look, no yellow/gold. Check: `gold-badge` is still used for points in the report and settings pages — compare with spec Part 10 and fix if it conflicts.
- Developer credit: মো: মামুনুর রশিদ. Feedback via WhatsApp +8801646464778.
- Friday (Jumu'ah) changes apply only on Fridays; other days must look identical to a build without Friday logic.

## Font
Owner prefers Kohinoor (`Kohinoor_Comforta.ttf`); the spec says Kalpurush. Neither file is in the repo. Currently Noto Sans Bengali from Google Fonts (needs internet). If the owner provides a .ttf, put it in `app/src/main/assets/` and add an `@font-face` so it works offline.

## Known limits
- `window.print()` (report PDF button) does not work inside WebView.
- Google Fonts needs internet; offline falls back to the phone's Bengali font.

## How to work with the owner
- Small steps. Say exactly what changed. Do not claim "verified" unless you actually ran/checked it.
- Owner tests on a phone and sends screenshots; every new screen is shown to him before it is called done.
- To build: push to GitHub → Actions → "Build APK" → download artifact `deen-daily-apk`. Local alternative: `gradle assembleDebug` (needs JDK 17 + Android SDK).
