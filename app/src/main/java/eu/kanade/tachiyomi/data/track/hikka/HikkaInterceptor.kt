package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.track.hikka.dto.HKAuthTokenInfo
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

class HikkaInterceptor(private val hikka: Hikka, private val json: Json) : Interceptor {
    private var oauth: HKOAuth? = hikka.loadOAuth()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Hikka: You are not authorized")

        if (currAuth.isExpired()) {
            val refreshTokenResponse = chain.proceed(HikkaApi.refreshTokenRequest(currAuth.accessToken))
            if (!refreshTokenResponse.isSuccessful) {
                refreshTokenResponse.close()
                hikka.logout()
                throw Exception("Hikka: The token is expired")
            } else {
                refreshTokenResponse.close()
            }

            val authTokenInfoResponse = chain.proceed(HikkaApi.authTokenInfo(currAuth.accessToken))
            if (!authTokenInfoResponse.isSuccessful) {
                authTokenInfoResponse.close()
                throw Exception("Hikka: Auth token info failed")
            }

            val authTokenInfo = with(json) {
                authTokenInfoResponse.parseAs<HKAuthTokenInfo>()
            }
            setAuth(HKOAuth(currAuth.accessToken, authTokenInfo.expiration, authTokenInfo.created))
        }

        val authRequest = originalRequest.newBuilder()
            .addHeader("auth", currAuth.accessToken)
            .addHeader("accept", "application/json")
            .build()

        return chain.proceed(authRequest)
    }

    fun setAuth(oauth: HKOAuth?) {
        this.oauth = oauth
        hikka.saveOAuth(oauth)
    }
}
