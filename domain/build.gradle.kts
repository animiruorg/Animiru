plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.domain"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    // AM (GROUPING) -->
    implementation(projects.i18n)
    implementation(projects.i18nAniyomi)
    implementation(projects.i18nAnimiru)
    // <-- AM (GROUPING)

    implementation(libs.metro.runtime)
    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.bundles.serialization)

    implementation(libs.kotlinx.datetime)

    implementation(libs.unifile)

    // AM (CUSTOM_INFORMATION) -->
    implementation(libs.injekt)
    // <-- AM (CUSTOM_INFORMATIO)

    api(libs.androidx.paging.runtime)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)
    // AM (CAST) -->
    compileOnly(libs.androidx.compose.foundation)
    // <-- AM (CAST)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
