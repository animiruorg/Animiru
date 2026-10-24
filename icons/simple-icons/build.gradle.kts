import mihon.gradle.tasks.GenerateVectorDrawablesTask

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(mihonx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.simpleicons"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "mihon.icons.simpleicons"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SimpleIcons"
        targetSourceSet = "main"
    }
}

// AM -->
androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateVectorDrawablesTask>("generate${variantName}VectorDrawables") {
            svgFiles.from(
                listOf(
                    "discord",
                ).map { layout.projectDirectory.file("src/main/valkyrieResources/$it.svg") },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
    }
}
// <-- AM

dependencies {
    api(libs.androidx.compose.ui)
}
