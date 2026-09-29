# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## [Unreleased]
### Improved
- Long press on go back in anime screen now goes to parent anime ([@Secozzi](https://github.com/Secozzi)) ([#220](https://github.com/animiruorg/Animiru/pull/220))
- Make the scrollbar in an anime entry less buggy when scrolling and dragging the thumb ([@Secozzi](https://github.com/Secozzi)) ([#229](https://github.com/animiruorg/Animiru/pull/229))

### Fixed
- Fix episode thumbnails not being fetched with source headers ([@Secozzi](https://github.com/Secozzi)) ([#225](https://github.com/animiruorg/Animiru/pull/225))
- Fix animiru crashing for rpc when rpc is turned off ([@Secozzi](https://github.com/Secozzi)) ([#228](https://github.com/animiruorg/Animiru/pull/228))
- Fixed entries being incorrectly marked as duplicates when tracked with some trackers ([@Secozzi](https://github.com/Secozzi)) ([#230](https://github.com/animiruorg/Animiru/pull/230))

## [v0.20.0.1] - 2026-09-14
### Fixed
- Fixed per-category sorting mode ([@Secozzi](https://github.com/Secozzi)) ([#209](https://github.com/animiruorg/Animiru/pull/209))

## [v0.20.0.0] - 2026-09-14
### Added
- Added chromecast support ([@Secozzi](https://github.com/Secozzi)) ([#175](https://github.com/animiruorg/Animiru/pull/175))
- Add support for memo-based jellyfin ext ([@Secozzi](https://github.com/Secozzi)) ([#199](https://github.com/animiruorg/Animiru/pull/199))

### Improved
- Don't toggle controls visibility when double tap seeking ([@Secozzi](https://github.com/Secozzi)) ([#199](https://github.com/animiruorg/Animiru/pull/199))
- Brightness can be changed from quick settings before any drag gestures ([@Secozzi](https://github.com/Secozzi)) ([#199](https://github.com/animiruorg/Animiru/pull/199))
- Set navigation bar background same as system navigation ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))
- Add support for jellyfin 12.0 ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))
- Show a helpful error message for expired AniList credentials ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))
- Don't include tip on new updates screen ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))

### Fixed
- Fix discord rpc ([@Secozzi](https://github.com/Secozzi)) ([#196](https://github.com/animiruorg/Animiru/pull/196))
- Fix search intent using manga instead of anime ([@Secozzi](https://github.com/Secozzi)) ([#197](https://github.com/animiruorg/Animiru/pull/197))
- Fix simkl not fetching last watched time ([@Secozzi](https://github.com/Secozzi)) ([#198](https://github.com/animiruorg/Animiru/pull/198))
- Fix track sync breaking if source isn't loaded ([@Secozzi](https://github.com/Secozzi)) ([#199](https://github.com/animiruorg/Animiru/pull/199))
- Fixed default category and anime sometimes not getting their category set when restoring a backup ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))
- Fixed AniList rate limit ([@Secozzi](https://github.com/Secozzi)) ([#208](https://github.com/animiruorg/Animiru/pull/208))

### Other
- Merge from aniyomi ([@Secozzi](https://github.com/Secozzi)) ([#191](https://github.com/animiruorg/Animiru/pull/191), [#200](https://github.com/animiruorg/Animiru/pull/200))
- Merge from mihon and aniyomi ([@Secozzi](https://github.com/Secozzi)) ([#192](https://github.com/animiruorg/Animiru/pull/192), [#207](https://github.com/animiruorg/Animiru/pull/207))

## [v0.19.8.1] - 2026-08-09
### Improved
- Make some more mpv options overridable by mpv.conf ([@Secozzi](https://github.com/Secozzi)) ([#187](https://github.com/animiruorg/Animiru/pull/187))
- Add language indicator for external tracks ([@Secozzi](https://github.com/Secozzi)) ([#189](https://github.com/animiruorg/Animiru/pull/189))

### Fixed
- Fix current chapter not indicator sometimes not showing ([@Secozzi](https://github.com/Secozzi)) ([#188](https://github.com/animiruorg/Animiru/pull/188))

## [v0.19.8.0] - 2026-08-03
### Improved
- Always display navigation bar labels & implement rail in tablet mode ([@Secozzi](https://github.com/Secozzi)) ([#181](https://github.com/animiruorg/Animiru/pull/181))

### Fixed
- Fix entering pip not working for extreme aspect ratios ([@Secozzi](https://github.com/Secozzi)) ([#176](https://github.com/animiruorg/Animiru/pull/176))
- Fix hardware decoding not preferring HW+ ([@Secozzi](https://github.com/Secozzi)) ([#177](https://github.com/animiruorg/Animiru/pull/177))
- Display current chapter indicator if position is before first chapter ([@Secozzi](https://github.com/Secozzi)) ([#183](https://github.com/animiruorg/Animiru/pull/183))
- Fix audio selection not showing for external audio tracks ([@Secozzi](https://github.com/Secozzi)) ([#184](https://github.com/animiruorg/Animiru/pull/184))
- Fix "Don't skip" skipping for netflix style skipping ([@Secozzi](https://github.com/Secozzi)) ([#185](https://github.com/animiruorg/Animiru/pull/185))
- Fix aniskip running even if "Disable AniSkip if video already contains chapters" is on ([@Secozzi](https://github.com/Secozzi)) ([#185](https://github.com/animiruorg/Animiru/pull/185))

## [v0.19.7.9] - 2026-07-08
### Fixed
- Fix player crash when opening player ([@Secozzi](https://github.com/Secozzi)) ([#170](https://github.com/animiruorg/Animiru/pull/170))

## [v0.19.7.8] - 2026-07-07
### Added
- Added option to skip broken tracks on download ([@Secozzi](https://github.com/Secozzi)) ([#169](https://github.com/animiruorg/Animiru/pull/169))

### Other
- Refactor video player code ([@Secozzi](https://github.com/Secozzi)) ([#167](https://github.com/animiruorg/Animiru/pull/167))

## [v0.19.7.7] - 2026-06-24
### Fixed
- Fix custom buttons not being added ([@Secozzi](https://github.com/Secozzi)) ([#164](https://github.com/animiruorg/Animiru/pull/164))
- Fix tracks not loading after changing quality ([@Secozzi](https://github.com/Secozzi)) ([#165](https://github.com/animiruorg/Animiru/pull/165))

## [v0.19.7.6] - 2026-06-12
### Improved
- Don't start playing until external tracks are loaded and ready ([@Secozzi](https://github.com/Secozzi)) ([#160](https://github.com/animiruorg/Animiru/pull/160))

### Fixed
- Fix tracks not being selected when switching episodes ([@Secozzi](https://github.com/Secozzi)) ([#160](https://github.com/animiruorg/Animiru/pull/160))

## [v0.19.7.5] - 2026-06-08
### Improved
- Add order priority for whitelist in track select ([@Secozzi](https://github.com/Secozzi)) ([#159](https://github.com/animiruorg/Animiru/pull/159))

### Fixed
- Fix subtitle list not updating when selecting ([@Secozzi](https://github.com/Secozzi)) ([#159](https://github.com/animiruorg/Animiru/pull/159))
- Fix subtitle & audio track list not updating properly when changing episodes ([@Secozzi](https://github.com/Secozzi)) ([#159](https://github.com/animiruorg/Animiru/pull/159))

## [v0.19.7.4] - 2026-05-27
### Added
- Added system font fallback ([@Secozzi](https://github.com/Secozzi)) ([#156](https://github.com/animiruorg/Animiru/pull/156))

### Other
- Merge from Mihon ([@Secozzi](https://github.com/Secozzi)) ([#155](https://github.com/animiruorg/Animiru/pull/155))

## [v0.19.7.3] - 2026-04-17
### Improved
- Allow options in mpv.conf to override options set by `AniyomiMPVView` ([@Secozzi](https://github.com/Secozzi)) ([#151](https://github.com/animiruorg/Animiru/pull/151))

### Fixed
- Fixed player crash when running out of available videos ([@Secozzi](https://github.com/Secozzi)) ([#150](https://github.com/animiruorg/Animiru/pull/150))

## [v0.19.7.2] - 2026-04-13
### Fixed
- Fixed app crash ([@Secozzi](https://github.com/Secozzi)) ([#148](https://github.com/animiruorg/Animiru/pull/148))

## [v0.19.7.1] - 2026-04-13
### Improved
- Copy over mpv files on app resume ([@Secozzi](https://github.com/Secozzi)) ([#143](https://github.com/animiruorg/Animiru/pull/143))

### Fixed
- Bump mpv ([@Secozzi](https://github.com/Secozzi)) ([#144](https://github.com/animiruorg/Animiru/pull/144))
- Fix being able to add unsupported trackers to entries with seasons ([@Secozzi](https://github.com/Secozzi)) ([#147](https://github.com/animiruorg/Animiru/pull/147))

## [v0.19.7.0] - 2026-03-30
### Added
- Add parent title to user-data in lua ([@Secozzi](https://github.com/Secozzi)) ([#142](https://github.com/animiruorg/Animiru/pull/142))

### Fixed
- Fix "Override ASS/SSA subtitles" option ([@Secozzi](https://github.com/Secozzi)) ([#141](https://github.com/animiruorg/Animiru/pull/141))

### Other
- Merged from Mihon ([@Secozzi](https://github.com/Secozzi)) ([#136](https://github.com/animiruorg/Animiru/pull/136))

## [v0.19.4.2] - 2026-03-30
### Added
- Added season support for (enhanced) trackers ([@Secozzi](https://github.com/Secozzi)) ([#139](https://github.com/animiruorg/Animiru/pull/139))
- Added smart sync option for seasons ([@Secozzi](https://github.com/Secozzi)) ([#140](https://github.com/animiruorg/Animiru/pull/140))

### Improved
- Improved two-way sync for enhanced trackers ([@Secozzi](https://github.com/Secozzi)) ([#138](https://github.com/animiruorg/Animiru/pull/138))

### Fixed
- Fixed Jellyfin tracking for movies and entries with no episodes ([@Secozzi](https://github.com/Secozzi)) ([#140](https://github.com/animiruorg/Animiru/pull/140))

## [v0.19.4.1] - 2026-03-15
### Improved
- Added option to toggle subtitle rendering on black bars ([@Secozzi](https://github.com/Secozzi)) ([#134](https://github.com/animiruorg/Animiru/pull/134))
- Remove line limit for videos in quality sheet ([@Secozzi](https://github.com/Secozzi)) ([#135](https://github.com/animiruorg/Animiru/pull/135))

## [v0.19.4.0] - 2026-02-26
### Other
- Merged from Mihon ([@Secozzi](https://github.com/Secozzi)) ([#131](https://github.com/animiruorg/Animiru/pull/131))

## [v0.19.3.2] - 2026-02-23
### Added
- Added option to automatically select another video on failure to load current one ([@Secozzi](https://github.com/Secozzi)) ([#132](https://github.com/animiruorg/Animiru/pull/132))
- Added `show_seek_text` to lua bridge ([@Secozzi](https://github.com/Secozzi)) ([#132](https://github.com/animiruorg/Animiru/pull/132))

### Improved
- External subtitle tracks only load on selection ([@Secozzi](https://github.com/Secozzi)) ([#132](https://github.com/animiruorg/Animiru/pull/132))
- Chapter skipping for intro skip actually seeks by chapter([@Secozzi](https://github.com/Secozzi)) ([#132](https://github.com/animiruorg/Animiru/pull/132))

### Fixed
- Fixed start screen setting not working ([@Secozzi](https://github.com/Secozzi)) ([#128](https://github.com/animiruorg/Animiru/pull/128))

## [v0.19.3.1] - 2025-12-25
### Fixed
- Make the scrollbar on the anime screen less buggy ([@Secozzi](https://github.com/Secozzi)) ([#118](https://github.com/animiruorg/Animiru/pull/118))

## [v0.19.3.0] - 2025-12-25
### Fixed
- Fix navigation pill background disappearing on older devices ([@Secozzi](https://github.com/Secozzi)) ([#114](https://github.com/animiruorg/Animiru/pull/114))
- Fix anilist format nullability breaking search ([@Secozzi](https://github.com/Secozzi)) ([#116](https://github.com/animiruorg/Animiru/pull/116))

### Other
- Merged from Aniyomi and Mihon ([@Secozzi](https://github.com/Secozzi)) ([#115](https://github.com/animiruorg/Animiru/pull/115))

## [v0.19.0.0] - 2025-12-24
### Changed
- Remove circular edges, add background and sliding animations ([@Quickdev](https://github.com/quickdesh)) ([`8e45259`](https://github.com/animiruorg/Animiru/commit/8e45259))
- Use filter chips in recents tab ([@Quickdev](https://github.com/quickdesh)) ([`38c9c52`](https://github.com/animiruorg/Animiru/commit/38c9c52))

### Fixed
- Fix formatting of file size ([@Quickdev](https://github.com/quickdesh)) ([`958e245`](https://github.com/animiruorg/Animiru/commit/958e245))
- Don't overwrite episodes.json with anime details for localanime ([@Secozzi](https://github.com/Secozzi)) ([#96](https://github.com/animiruorg/Animiru/pull/96))
- Fix jellyfin enhanced tracker for newer versions of the extension ([@Secozzi](https://github.com/Secozzi)) ([#107](https://github.com/animiruorg/Animiru/pull/107))

### Other
- Merged from Aniyomi and Mihon ([@Secozzi](https://github.com/Secozzi)) ([#102](https://github.com/animiruorg/Animiru/pull/102) [#110](https://github.com/animiruorg/Animiru/pull/110))
- Add support for extension lib 16 ([@Secozzi](https://github.com/Secozzi)) ([#104](https://github.com/animiruorg/Animiru/pull/104))

## [v0.17.2.0] - 2024-07-27
### Fixes
- Fix extensions screen padding and loading ([@Quickdev](https://github.com/quickdesh)) ([`8e6eb30`](https://github.com/animiruorg/Animiru/commit/8e6eb30))
- Fix navigation pill tab swiping ([@Quickdev](https://github.com/quickdesh)) ([`87a246e`](https://github.com/animiruorg/Animiru/commit/87a246e))
- Fix Google drive sync ([@Quickdev](https://github.com/quickdesh)) ([`8af7c9a`](https://github.com/animiruorg/Animiru/commit/8af7c9a))
- Temporarily disable airing time sort ([@Quickdev](https://github.com/quickdesh)) ([`9637c8c`](https://github.com/animiruorg/Animiru/commit/9637c8c))

### Other
- Removed unused libraries ([@Quickdev](https://github.com/quickdesh)) ([`483dad9`](https://github.com/animiruorg/Animiru/commit/483dad9))

## [v0.17.1.0] - 2024-06-11
### Added
- Add long pressing navigation tabs ([@Quickdev](https://github.com/quickdesh)) ([`b4b1e07`](https://github.com/animiruorg/Animiru/commit/b4b1e07))

### Changed
- Remove release filter from private installer ([@Quickdev](https://github.com/quickdesh)) ([`a6a7799`](https://github.com/animiruorg/Animiru/commit/a6a7799))

### Fixed
- Fix crash when opening a new extension's settings ([@Quickdev](https://github.com/quickdesh)) ([`d90f059`](https://github.com/animiruorg/Animiru/commit/d90f059))

[unreleased]: https://github.com/animiruorg/Animiru/compare/v0.20.0.1...animiru-new-main
[v0.20.0.1]: https://github.com/animiruorg/Animiru/compare/v0.20.0.0...v0.20.0.1
[v0.20.0.0]: https://github.com/animiruorg/Animiru/compare/v0.19.8.1...v0.20.0.0
[v0.19.8.1]: https://github.com/animiruorg/Animiru/compare/v0.19.8.0...v0.19.8.1
[v0.19.8.0]: https://github.com/animiruorg/Animiru/compare/v0.19.7.9...v0.19.8.0
[v0.19.7.9]: https://github.com/animiruorg/Animiru/compare/v0.19.7.8...v0.19.7.9
[v0.19.7.8]: https://github.com/animiruorg/Animiru/compare/v0.19.7.7...v0.19.7.8
[v0.19.7.7]: https://github.com/animiruorg/Animiru/compare/v0.19.7.6...v0.19.7.7
[v0.19.7.6]: https://github.com/animiruorg/Animiru/compare/v0.19.7.5...v0.19.7.6
[v0.19.7.5]: https://github.com/animiruorg/Animiru/compare/v0.19.7.4...v0.19.7.5
[v0.19.7.4]: https://github.com/animiruorg/Animiru/compare/v0.19.7.3...v0.19.7.4
[v0.19.7.3]: https://github.com/animiruorg/Animiru/compare/v0.19.7.2...v0.19.7.3
[v0.19.7.2]: https://github.com/animiruorg/Animiru/compare/v0.19.7.1...v0.19.7.2
[v0.19.7.1]: https://github.com/animiruorg/Animiru/compare/v0.19.7.0...v0.19.7.1
[v0.19.7.0]: https://github.com/animiruorg/Animiru/compare/v0.19.4.2...v0.19.7.0
[v0.19.4.2]: https://github.com/animiruorg/Animiru/compare/v0.19.4.1...v0.19.4.2
[v0.19.4.1]: https://github.com/animiruorg/Animiru/compare/v0.19.4.0...v0.19.4.1
[v0.19.4.0]: https://github.com/animiruorg/Animiru/compare/v0.19.3.2...v0.19.4.0
[v0.19.3.2]: https://github.com/animiruorg/Animiru/compare/v0.19.3.1...v0.19.3.2
[v0.19.3.1]: https://github.com/animiruorg/Animiru/compare/v0.19.3.0...v0.19.3.1
[v0.19.3.0]: https://github.com/animiruorg/Animiru/compare/v0.19.0.0...v0.19.3.0
[v0.19.0.0]: https://github.com/animiruorg/Animiru/compare/v0.17.2.0...v0.19.0.0
[v0.17.2.0]: https://github.com/animiruorg/Animiru/compare/v0.17.1.0...v0.17.2.0
[v0.17.1.0]: https://github.com/animiruorg/Animiru/compare/v0.17.0.0...v0.17.1.0
