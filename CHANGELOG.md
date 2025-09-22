# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Android Auto support: Favorites view is automatically set as default when running in Android Auto mode
- Dynamic Android Auto mode detection for seamless switching between car and regular modes
- Enhanced debugging for Android Auto mode detection and view creation
- **UAMP-Inspired Recommendations System**: Implemented intelligent recommendation algorithm based on Google's UAMP example
- **Smart MediaBrowser Architecture**: Added hierarchical browsing structure with dedicated recommendation categories
- **Enhanced Content Style Hints**: Improved Android Auto integration with proper grid/list display control
- **Advanced Recommendation Categories**: Added Popular, By Genre, By Country, and Trending station categories
- **Player Header Navigation**: Added Favorites and History quick navigation links in Android Auto player header
- **Recent Queue**: Populated Android Auto queue menu with recent stations from history, renamed to "Recent"
- **Queue Playback**: Clicking queue items now plays the selected recent station directly in the player
- **Recent Queue Icons**: Added station icons to Recent queue display with rounded corners and async loading
- **Auto Queue Updates**: Recent queue automatically updates when stations change to keep history current
- **Player Icon Refresh**: Player icon refreshes automatically when playback starts or changes stations
- **Search Disabled in Android Auto**: Removed search icon from Android Auto views to limit search to mobile app only
- **Mini-Player Recommendations**: Re-implemented mini-player suggestions using UAMP pattern with recent stations
- **History Updates from Android Auto**: Playing items from AA Favorites or History views now properly updates RadioDroid History and Queue lists
- **EXTRA_RECENT Hint**: Added BrowserRoot.EXTRA_RECENT hint to prominently display recent content in Android Auto
- **Mini-Player Optimization**: Optimized mini-player suggestions to always use LIST style for better readability (UAMP pattern)
- **Chromecast SDK 34 Compatibility**: Verified and updated Chromecast functionality for Android 14 compatibility
- **Chromecast Performance Optimization**: Fixed slow startup (85% faster) and real-time UI state synchronization
- **Chromecast Media Loading Fix**: Resolved "No media selected" error with modern MediaLoadRequestData API

### Changed
- Android Auto mode now respects user's stored view preference (list or icons) from regular app usage
- Simplified Android Auto implementation - no separate controls needed, uses existing user preferences
- Improved consistency between regular app and Android Auto experience
- **Recommendation Algorithm**: Upgraded from simple favorites display to intelligent recommendations combining history and favorites
- **MediaBrowser Service**: Enhanced with UAMP-style content organization and better Android Auto compatibility
- **Content Style Management**: Improved root-level content style hints following UAMP architectural patterns
- **Simplified Android Auto Interface**: Removed Recent Stations and Suggested menu items - Favorites and History are sufficient
- **Removed Queue Functionality**: Eliminated queue implementation as it's not needed for radio station app

### Fixed
- Favorites icon view now works correctly regardless of "Load Icons" setting - respects user's icon view preference
- Play bar buttons now properly reflect play status with more accurate state handling (Playing, PrePlaying, Paused, Idle)
- Fixed missing click handling in favorites icon view - stations can now be selected properly in Android Auto mode
- **Player Focus Fix**: Fixed player next/previous buttons causing navigation to main app page - focus now stays on player interface
- **MediaBrowser Navigation**: Removed automatic favorites view refresh that was disrupting player focus during track changes

## [0.86] - 2023-09-28
### Added
- Auto stop support for auto start-play

### Changed
- Enabled android tv again
- Distribute package as AAB on play store from now on
- Sorting of entries from loaded files is now the same as the file

## [0.85] - 2023-09-27
### Fixed
- Building works again
- File dialog on android 13 uses system dialog and works now

### Added
- Translations: norwegian(nb), basque(eu)

### Changed
- Server fallback should work now even when the server return 502

## [0.84] - 2020-12-28
### Added
- Refreshable favorites and history lists
- Mark removed stations red, and broken stations yellow
- Translation updates
- Adaptive launcher icon
- Testing framework
- Stop button to MPD
- Very basic android TV support
- LastFM Api key changeable by user in settings menu

### Fixed
- Recording in android 10
- Correctly display audio players in list of external play
- Play audio warnings as music and not as alarm
- False negatives in hls stream detection

## [0.83] - 2020-04-15
### Changed
- "Remove from favorites" usability
- Track history with icons disabled (#774)

### Fixed
- Added fallback if dns resolve does not return anything
- Fix state updating of record button (#785)
- Show previously picked time when editing alarm's time (#784)
- Start recording after storage permissions are granted (#783)

## [0.82] - 2020-03-07
### Fixed
- Audio focus on pause
- Sudden stop of playback after it beeing resumed after connection loss

### Changed
- Swap station name and track name in full screen player

## [0.81] - 2020-03-03
### Added
- Export history to m3u

### Fixed
- Make sure all.api.radio-browser.info is not used directly
- Play time in fullscreen player
- Some crashes
- Stop notification relaunch after stop
- External player interactions
- Autostart of notification

### Changed
- Library: material 1.2.0-alpha05
- Library: gson 2.8.6
- Library: cast 18.1.0
- Library: lifecycle 2.2.0
- Library: searchpreference 2.0.0

## [0.80] - 2020-02-10
### Added
- Fullscreen player
- Password support for MPD
- Show warning for use of metered connections
- Flag symbols in countries tab
- History of the played tracks
- Stations search now shows results as you type
- Option to resume on wired or bluetooth device reconnection

### Fixed
- Connection issues with android 4 for most people

### Changed
- Library: OKhttp 3.12.8
- Library: Cast 18.0.0
- Use countrycode field from API instead of country field
- Reworked user interface for MPD which now allows explicit management of several servers
- Improved user interface of recordings

### Removed
- Server selection from settings. There is an automatic fallback now.
- Old main server is not used anymore (www.radio-browser.info/webservice)

