# Types extensions implement, extend or construct
-keep class eu.kanade.tachiyomi.animesource.AnimeSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.AnimeSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.AnimeCatalogueSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.AnimeCatalogueSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.AnimeSourceFactory { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.UnmeteredSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.AnimeFilter { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.AnimeFilter$* { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.AnimeFilterList { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.AnimesPage { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.Video { public protected *; }
# AM -- >
-keep class eu.kanade.tachiyomi.animesource.model.AnimeRelation { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.ChapterType { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.FetchType { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.HttpServer { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.HttpServer$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.Hoster { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.Hoster$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.ThumbnailInfo { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.TileInfo { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.TimeStamp { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.Track { public protected *; }
# <-- AM
-keep class eu.kanade.tachiyomi.animesource.model.SAnime { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.SAnime$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.SEpisode { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.SEpisode$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.online.AnimeHttpSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource { public protected *; }

# Final classes and top-level functions extensions only call into
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.HttpException { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.JavaScriptEngine { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
