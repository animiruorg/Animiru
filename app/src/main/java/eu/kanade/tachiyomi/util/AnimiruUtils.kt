// AM (CUSTOM_INFORMATION) -->
package eu.kanade.tachiyomi.util

fun String.trimOrNull() = trim().nullIfBlank()

fun String.nullIfBlank(): String? = ifBlank { null }

fun <C : Collection<R>, R> C.nullIfEmpty() = ifEmpty { null }
// <-- AM (CUSTOM_INFORMATION)
