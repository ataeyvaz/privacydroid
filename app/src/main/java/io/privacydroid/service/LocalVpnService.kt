package io.privacydroid.service

import android.content.Context

/** PrivacyVpnService'e yönlendirici — geriye dönük uyumluluk için tutuldu. */
object LocalVpnService {
    fun startVpn(context: Context) = PrivacyVpnService.startVpn(context)
    fun stopVpn(context: Context)  = PrivacyVpnService.stopVpn(context)
}
