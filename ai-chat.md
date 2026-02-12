# AI Chat - Code Review & Changes Log

## Session: 2026-02-12

---

## Part 1: Feature Requests

### 1. Extra Large Icon Size

**Request:** Add an option for extra large icons.

**Implementation:** Added a 4th icon size option (96dp, 1-column grid) alongside the existing Small (36dp/4-col), Medium (48dp/3-col), Large (64dp/2-col).

**Files changed:**
- `PrefsManager.kt` - Added `ICON_SIZE_EXTRA_LARGE = 3` constant
- `DisplaySettingsActivity.kt` - Added "Extra Large" to `ICON_SIZE_OPTIONS` array and `updateIconSizeLabel()`
- `AppDrawerActivity.kt` - Added extra large case to `getColumnCount()` (1 col) and `iconSizeDp` (96dp)

### 2. Greyscale Toggle Without ADB

**Request:** Adjust the greyscale option so that it lets users enable the setting change from the phone (without needing a computer for ADB).

**Implementation:** Updated the fallback dialog when `WRITE_SECURE_SETTINGS` isn't granted. Instead of telling the user to run an ADB command, it now offers two buttons:
- **ACCESSIBILITY** (primary) - opens Android Accessibility Settings where Color Correction > Greyscale can be enabled
- **BEDTIME MODE** (secondary) - opens Digital Wellbeing Bedtime Mode settings

Also added `onResume()` sync that reads the actual system daltonizer state (`Settings.Secure.getInt("accessibility_display_daltonizer_enabled")`) and updates the toggle to match, so after the user changes it in system settings and returns, the toggle reflects reality.

**Files changed:**
- `DisplaySettingsActivity.kt` - Updated `setSystemGreyscale()` fallback, added `onResume()` sync
- `activity_display_settings.xml` - Updated hint text to mention Accessibility path

### 3. Temporary Black Wallpaper

**Request:** Can the launcher change the home screen wallpaper to match the launcher's black screen while using it? Not permanently, only whilst using the launcher.

**Implementation:** Added a "Override wallpaper" toggle in Settings. When enabled:
1. Backs up current wallpaper to `wallpaper_backup.png` in app internal storage
2. Sets system wallpaper to solid black (1x1 black bitmap)

When disabled:
1. Restores the backed-up wallpaper via `WallpaperManager.setBitmap()`
2. Deletes the backup file
3. Falls back to `WallpaperManager.clear()` if no backup exists

On `MainActivity.onResume()`: enforces black wallpaper if setting is enabled (once per process lifecycle, not every resume - see fix below).

**Files changed:**
- `AndroidManifest.xml` - Added `SET_WALLPAPER` permission
- `PrefsManager.kt` - Added `overrideWallpaper` boolean preference
- `activity_settings.xml` - Added wallpaper toggle + hint text
- `SettingsActivity.kt` - Added `enableWallpaperOverride()` and `disableWallpaperOverride()` methods
- `MainActivity.kt` - Added `enforceWallpaperOverride()` called from `onResume()`

**Limitation:** On some Android versions/OEM builds, reading the current wallpaper drawable may fail or return an unexpected result, so auto-backup is not guaranteed. If backup fails, users may need to re-set wallpaper manually after disabling override.

---

## Part 2: Code Review Findings & Responses

### High: README/docs vs actual permissions mismatch

**Finding:** README claimed "no internet permission" and "minimal permissions" but the app uses INTERNET, ACCESS_COARSE_LOCATION, SET_WALLPAPER, and WRITE_SECURE_SETTINGS.

**Response:** Agree, this should be fixed. The README needed to honestly document all permissions.

**Action taken:** Updated README.md:
- Removed false "no internet" and "home screen widgets" claims
- Added actual features (weather, greyscale, DND, wallpaper override, adjustable icons)
- Replaced Security section with a permissions table documenting all 6 permissions, required vs optional, and purpose
- Added note about `WRITE_SECURE_SETTINGS` Play Store implications
- Updated Settings section and project structure to match actual codebase

### High: WRITE_SECURE_SETTINGS distribution risk

**Finding:** `WRITE_SECURE_SETTINGS` is a signature-level permission that creates policy/distribution risk for store-distributed builds.

**Response:** Partially agree. The permission is already gated properly - declared in manifest but only works if ADB-granted, and the code catches `SecurityException` gracefully and redirects to Accessibility Settings. It won't cause runtime issues on Play Store (permission simply won't be granted), but the manifest declaration could trigger review flags.

**Recommendation:** Keep for sideloaded/dev builds since this is a "dumbphone" launcher likely sideloaded anyway. Consider build flavors (main vs Play Store) if store distribution is planned. Added a note in README about this.

**Action taken:** No code change. Documented the risk in README.

### High: Wallpaper override rewrites system wallpaper on every resume

**Finding:** `enforceWallpaperOverride()` called `setBitmap()` on every `MainActivity.onResume()`, which is expensive and battery-draining.

**Response:** Agree, this was the most actionable fix.

**Action taken:** Added an in-memory `wallpaperOverrideApplied` flag. `setBitmap()` now only fires once per process lifecycle:
- First resume after enabling: applies wallpaper, sets flag to true
- Subsequent resumes: skips (flag is true)
- Feature disabled: resets flag to false
- Process killed and restarted: flag resets, applies once more

### Medium: Build quality gates disabled

**Finding:** `abortOnError false` and `warningsAsErrors false` in `build.gradle` reduce signal from lint.

**Response:** Fair point for a production app. Less critical for a personal/hobby project but should be tightened before any release, especially in CI/release builds.

**Action taken:** Enabled `abortOnError true` and `checkReleaseBuilds true` in `build.gradle` lint block. Suppressed known false positives (`MissingTranslation`, `HardcodedText`, `UseSwitchCompatOrMaterialCode`) since i18n and Material Switch migration are separate efforts.

### Medium: Performance inefficiencies on hot paths

**Finding:** `SimpleDateFormat` recreated every second in `updateClock()`. Per-item `ColorMatrix` allocation in `AppGridAdapter.onBindViewHolder()`.

**Response:** Valid but low-impact. A clock updating once per second creating a `SimpleDateFormat` won't cause visible jank on modern hardware. The `ColorMatrix` per RecyclerView item is slightly more concerning during fast scrolls but still marginal with the small list sizes involved (whitelisted apps only).

**Recommendation:** Worth fixing in a cleanup pass. Cache formatters as class-level fields and reuse a single `ColorMatrixColorFilter` instance.

**Action taken:**
- `MainActivity.kt`: Added `cachedTimeFormat` / `cachedTimePattern` fields. `SimpleDateFormat` is now only recreated when the pattern changes (user toggles 24h/seconds). Date formatter promoted to a class-level `dateFormat` field.
- `AppDrawerActivity.kt`: Created a single shared `monochromeFilter` (`ColorMatrixColorFilter`) and `fgColour` field in the adapter, reused across all `onBindViewHolder` calls instead of allocating per item.

### Medium: App search rebuilds RecyclerView adapter on each keystroke

**Finding:** `AppsSettingsActivity` creates a new adapter on each filter pass instead of using `ListAdapter` + `DiffUtil`.

**Response:** Agree. `ListAdapter` with `DiffUtil` would be cleaner and produce smoother scrolling with less GC churn. However, the current approach works fine for the small list sizes involved (~50-100 apps).

**Action taken:** Rewrote `AppToggleAdapter` to extend `ListAdapter<ResolveInfo, ViewHolder>` with a `DiffUtil.ItemCallback` that diffs by package name. `filterApps()` now calls `submitList()` on the existing adapter instead of creating a new one each keystroke.

### Medium: Weather networking robustness

**Finding:** `WeatherUtil.fetch()` doesn't check HTTP response codes and only disconnects on the success path.

**Response:** Agree. Should use `runCatching` + `use` + response code checks. Consider OkHttp/coroutines for cancellation/lifecycle safety, though that may be overkill for a single API call.

**Action taken:** Updated `WeatherUtil.fetch()`:
- Added HTTP response code check (returns null on non-200)
- Used `bufferedReader().use { }` for proper stream cleanup
- Moved `conn.disconnect()` to a `finally` block so it runs on both success and error paths

### Low: Internationalisation/accessibility gaps

**Finding:** Most UI strings are hardcoded in layouts and code. `strings.xml` has only the app name.

**Response:** Valid for a production app. Low priority for a personal launcher unless multi-language support is actually wanted.

**Action taken:** Deferred. Not in scope for this pass -- would require touching every layout and activity for a feature (i18n) that hasn't been requested.

### Low: README stale vs implementation (widgets)

**Finding:** README mentioned home screen widgets and `BIND_APPWIDGET` but no widget host/provider code exists.

**Response:** Agree. Stale claims should be removed.

**Action taken:** Removed widget references from README as part of the docs update.

---

## Part 3: Agreed Priority Order

1. **Stop wallpaper re-apply on every resume** - DONE (Owner: Nadav, Completed: 2026-02-12)
2. **Correct README/security claims to match reality** - DONE (Owner: Nadav, Completed: 2026-02-12)
3. **Decide release strategy for WRITE_SECURE_SETTINGS** (main vs dev-only flavor) - Documented, not yet implemented (Owner: Nadav, Target: 2026-02-19)
4. **Batch cleanup** - DONE (Owner: Nadav, Completed: 2026-02-12)
   - Lint tightening (`abortOnError true`, `checkReleaseBuilds true`)
   - Cached `SimpleDateFormat` and `ColorMatrixColorFilter`
   - `ListAdapter` + `DiffUtil` for app search
   - Weather networking robustness (response code check, `finally` disconnect, `use` for streams)
   - i18n/string resources: deferred (not requested, large scope)

---

## Files Modified (All Changes Combined)

| File | Changes | Evidence |
|---|---|---|
| `PrefsManager.kt` | Added `ICON_SIZE_EXTRA_LARGE`, `overrideWallpaper` pref | `app/src/main/java/com/dumbphone/launcher/PrefsManager.kt` |
| `DisplaySettingsActivity.kt` | Extra Large in picker, greyscale fallback to Accessibility, `onResume()` sync | `app/src/main/java/com/dumbphone/launcher/DisplaySettingsActivity.kt` |
| `AppDrawerActivity.kt` | Extra Large sizes, shared `monochromeFilter` and `fgColour` in adapter | `app/src/main/java/com/dumbphone/launcher/AppDrawerActivity.kt` |
| `MainActivity.kt` | Wallpaper enforcement with in-memory flag, cached `SimpleDateFormat` | `app/src/main/java/com/dumbphone/launcher/MainActivity.kt` |
| `SettingsActivity.kt` | Wallpaper override toggle, backup/restore logic | `app/src/main/java/com/dumbphone/launcher/SettingsActivity.kt` |
| `AppsSettingsActivity.kt` | Migrated to `ListAdapter` + `DiffUtil`, `submitList()` on filter | `app/src/main/java/com/dumbphone/launcher/AppsSettingsActivity.kt` |
| `WeatherUtil.kt` | Response code check, `finally` disconnect, `use` for stream | `app/src/main/java/com/dumbphone/launcher/WeatherUtil.kt` |
| `AndroidManifest.xml` | Added `SET_WALLPAPER` permission | `app/src/main/AndroidManifest.xml` |
| `activity_settings.xml` | Added wallpaper toggle + hint | `app/src/main/res/layout/activity_settings.xml` |
| `activity_display_settings.xml` | Updated greyscale hint text | `app/src/main/res/layout/activity_display_settings.xml` |
| `build.gradle` | Lint: `abortOnError true`, `checkReleaseBuilds true`, suppressed known false positives | `app/build.gradle` |
| `README.md` | Corrected features, permissions table, project structure | `README.md` |
| `.gitignore` | Removed `ai-chat.md` ignore entry so this log can be tracked | `.gitignore` |