package com.example.ads

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoupleViewModel
import com.example.ui.components.GradientButton
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

private const val REWARDED_INTERSTITIAL_ID = "ca-app-pub-2515520610901543/79462698608"
private const val COINS_PER_REWARD = 50L

/** Permite ganar monedas viendo y completando un anuncio bonificado. */
@Composable
fun RewardedCoinsButton(vm: CoupleViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    var rewardedAd by remember { mutableStateOf<RewardedInterstitialAd?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando anuncio…") }

    fun loadRewardedAd() {
        if (loading || rewardedAd != null) return
        loading = true
        status = "Preparando anuncio…"
        RewardedInterstitialAd.load(
            context,
            REWARDED_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedAd = ad
                    loading = false
                    status = "Mirá el anuncio completo y recibí $COINS_PER_REWARD coins."
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                    status = "El anuncio todavía no está disponible. Probá nuevamente en un momento."
                }
            }
        )
    }

    LaunchedEffect(Unit) { loadRewardedAd() }

    Column(modifier = modifier.fillMaxWidth()) {
        GradientButton(
            text = when {
                rewardedAd != null -> "🎁 Ver anuncio y ganar $COINS_PER_REWARD coins"
                loading -> "⏳ Cargando anuncio…"
                else -> "🔄 Volver a cargar anuncio"
            },
            onClick = {
                val ad = rewardedAd
                if (ad == null) {
                    loadRewardedAd()
                } else if (activity != null) {
                    rewardedAd = null
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            loadRewardedAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            status = "No se pudo mostrar el anuncio. Intentá nuevamente."
                            loadRewardedAd()
                        }
                    }
                    ad.show(activity) {
                        // AdMob invoca este callback solo cuando se ganó la recompensa.
                        vm.addCredits(COINS_PER_REWARD)
                        status = "¡Recompensa acreditada: $COINS_PER_REWARD coins!"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && (rewardedAd != null || activity != null)
        )
        Text(
            text = status,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            textAlign = TextAlign.Center,
            fontSize = 11.sp
        )
    }
}
