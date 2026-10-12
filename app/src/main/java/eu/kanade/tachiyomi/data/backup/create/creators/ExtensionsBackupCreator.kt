// AY -->
package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.content.pm.PackageManager
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.extension.ExtensionManager
import java.io.File

@Inject
class ExtensionsBackupCreator(
    private val context: Context,
    private val extensionManager: ExtensionManager,
) {

    suspend operator fun invoke(): List<BackupExtension> {
        val installedExtensions = mutableListOf<BackupExtension>()
        extensionManager.getLoadedExtensions().forEach {
            val packageName = it.pkgName
            val apk = File(
                context.packageManager
                    .getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    ).publicSourceDir,
            ).readBytes()
            installedExtensions.add(
                BackupExtension(packageName, apk),
            )
        }
        return installedExtensions
    }
}
// <-- AY
