package io.privacydroid.data.model

/**
 * DNS tabanlı reklam/tracker engelleme modu.
 *
 * OFF        → Hiçbir şey engellenmez; VPN yalnızca izler ve loglar.
 * BALANCED   → ad_domains.txt'deki net reklam domainleri engellenir.
 *              Tracker'lar engellenmez (yalnızca loglanır), CDN'ler korunur.
 * AGGRESSIVE → ad_domains.txt + tracker_domains.txt engellenir.
 *
 * Beyaz liste (ad_whitelist.txt) ve uygulama bazlı istisnalar HER ZAMAN önce
 * gelir; bu modlardan bağımsız olarak o domainler asla engellenmez.
 */
enum class BlockingMode {
    OFF,
    BALANCED,
    AGGRESSIVE;

    val isEnabled: Boolean get() = this != OFF

    companion object {
        fun fromString(value: String?): BlockingMode =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}
