package mihon.domain.migration.models

enum class MigrationFlag(val flag: Int) {
    EPISODE(0b00001),

    // AM -->
    SEASON(0b10000000),

    // <-- AM
    CATEGORY(0b00010),

    // 0b00100 was used for anime trackers
    CUSTOM_COVER(0b01000),

    // AY -->
    CUSTOM_BACKGROUND(0b1000000),

    // <-- AY
    NOTES(0b100000),
    REMOVE_DOWNLOAD(0b10000),
    ;

    companion object {
        fun fromBit(bit: Int): Set<MigrationFlag> {
            return buildSet {
                entries.forEach { entry ->
                    if (bit and entry.flag != 0) add(entry)
                }
            }
        }

        fun toBit(flags: Set<MigrationFlag>): Int {
            return flags.map { it.flag }
                .reduceOrNull { acc, mask -> acc or mask }
                ?: 0
        }
    }
}
