Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/animiruorg/Animiru#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Animiru!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/animiruorg/Animiru/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Keep In Mind

Do remember to mark each line of code that is either added or edited (excluding imports) specifying the reason behind the code.
Surround the new code with:

`AM (<Title of code>) --> <-- AM (<Title of code>)`

### Credits (Added / Edited code from upstream)

- **AM (REMOVE_TABBED_SCREENS)** --> Refactoring from Aniyomi code to Animiru!
- **AM (REMOVE_ACRA_FIREBASE)** --> Refactoring from Aniyomi code to Animiru!
- **AM (REMOVE_LIBRARIES)** --> Refactoring from Aniyomi code to Animiru!
- **AM (BROWSE)** --> Thank you Quickdesh!
- **AM (KEYBOARD_CONTROLS)** --> Thank you Quickdesh!
- **AM (NAVIGATION_PILL)** --> Thank you Quickdesh!
- **AM (TAB_HOLD)** --> Thank you Quickdesh!
- **AM (FILE_SIZE)** --> Thank you Khaled0!
- **AM (DISCORD_RPC)** --> Original library from dead8309/Kizzy, refactored code by 最高 man/Shivam. Thank you, both of you!
- **AM (CUSTOM_INFORMATION)** --> Copied from SY, Thank you jobobby4/syer!
- **AM (GROUPING)** --> Copied from SY, Thank you jobobby4/syer!
- **AM (RECENTS)** --> Idea inspired from J2K, Thank you Jays2Kings!
- **AM (SYNC, SYNC_DRIVE, SYNC_YOMI)** --> Original code in SyncYomi, copied from Kuukiyomi. Thank you Kaiserbh and Luftverbot!
- **AM (STORAGE_SCREEN)** --> Taken from Aniyomi and refactored to fit Animiru's codebase!
- **AM (RECENTS_FILTER_CHIP)** --> Thank you Quickdesh!

## Linting

To auto-fix some linting errors, run the `spotlessApply` Gradle task.

## Getting help

- Join [the Discord server](https://discord.gg/yDuHDMwxhv) for online help and to ask questions while developing.

# Translations

Translations are done externally via Weblate. See [our website](https://aniyomi.org/docs/contribute#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/aniyomiorg/aniyomi/blob/main/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/animiruorg/Animiru/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/animiruorg/Animiru/blob/main/app/build.gradle.kts)
