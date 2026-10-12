package mihon.gradle

import org.gradle.api.Project

interface BuildConfig {
    // AM -->
    val includeCast: Boolean

    // <-- AM
    val enableUpdater: Boolean
    val includeDependencyInfo: Boolean
}

private enum class Distribution {
    LOCAL,
    CI,
    GITHUB,
    FOSS,
}

val Project.Config: BuildConfig get() = object : BuildConfig {
    private val distribution: Distribution = project.providers.gradleProperty("dist").orNull
        ?.let { name ->
            Distribution.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unknown -Pdist=$name, expected one of $distributionNames")
        }
        ?: Distribution.LOCAL

    // AM -->
    override val includeCast: Boolean = project.flag("include-cast")
        ?: (distribution == Distribution.CI || distribution == Distribution.GITHUB)
    // <-- AM

    override val enableUpdater: Boolean = project.flag("enable-updater")
        ?: (distribution != Distribution.LOCAL)

    override val includeDependencyInfo: Boolean = project.flag("include-dependency-info") ?: false
}

private val distributionNames = Distribution.entries.joinToString { it.name.lowercase() }

private fun Project.flag(name: String): Boolean? = providers.gradleProperty(name).orNull
    ?.let { it.isEmpty() || it.toBoolean() }
