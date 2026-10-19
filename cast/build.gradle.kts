import mihon.gradle.Config

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
    alias(mihonx.plugins.compose)

    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tachiyomi.cast"

    sourceSets {
        getByName("main") {
            if (Config.includeCast) {
                kotlin.directories.add("src/gms/kotlin")
                res.directories.add("src/gms/res")
                manifest.srcFile("src/gms/AndroidManifest.xml")
            } else {
                kotlin.directories.add("src/noop/kotlin")
                manifest.srcFile("src/main/kotlin/AndroidManifest.xml")
            }
        }
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(projects.presentationCore)
    implementation(projects.sourceApi)
    implementation(projects.i18nAnimiru)
    implementation(projects.icons.customMaterialSymbols)
    implementation(projects.icons.materialSymbols)
    implementation(aniyomilibs.mediarouter)
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(libs.androidx.appCompat)
    implementation(libs.injekt)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.materialIcons)
    implementation(libs.androidx.compose.material3)

    if (Config.includeCast) {
        implementation(aniyomilibs.gms.cast)
    }
}
