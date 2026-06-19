package com.superduper.sonoswidget.sonos

import android.util.Log

sealed class FavoritePlayResult {
    data class Played(val favoriteTitle: String, val speakers: Int) : FavoritePlayResult()
    object NoSpeakers : FavoritePlayResult()
    data class FavoriteNotFound(val name: String) : FavoritePlayResult()
}

/**
 * Sonos operations the favorite-play needs, behind a seam so [SonosFavoritePlayer]
 * can be unit-tested without a network.
 */
interface FavoritePlayTransport {
    /** Discovered players for the target rooms (empty rooms = all). */
    fun targetPlayers(rooms: Set<String>): List<SonosPlayer>

    /** Sonos favorites (system-wide), or empty if unavailable. */
    fun favorites(): List<SonosFavorite>

    /** Group [members] under [lead] so they play in sync. */
    fun group(lead: SonosPlayer, members: List<SonosPlayer>)

    /** Play the favorite on [lead]; its group follows. */
    fun play(lead: SonosPlayer, favorite: SonosFavorite)
}

/**
 * Plays a Sonos Favorite (matched by name) across the target rooms: it groups them
 * under one lead and plays the favorite there, leaving them grouped and playing.
 */
class SonosFavoritePlayer(private val transport: FavoritePlayTransport) {

    fun play(favoriteName: String, rooms: Set<String>): FavoritePlayResult {
        val players = transport.targetPlayers(rooms).sortedBy { it.roomName }
        if (players.isEmpty()) {
            Log.w(TAG, "No target speakers for rooms=$rooms")
            return FavoritePlayResult.NoSpeakers
        }

        val favorite = transport.favorites()
            .firstOrNull { it.title.trim().equals(favoriteName.trim(), ignoreCase = true) }
        if (favorite == null) {
            Log.w(TAG, "Favorite '$favoriteName' not found")
            return FavoritePlayResult.FavoriteNotFound(favoriteName)
        }

        val lead = players.first()
        transport.group(lead, players.drop(1))
        transport.play(lead, favorite)
        Log.i(TAG, "Playing '${favorite.title}' on ${players.size} speaker(s): ${players.map { it.roomName }}")
        return FavoritePlayResult.Played(favorite.title, players.size)
    }

    companion object {
        private const val TAG = "SonosWidget"
    }
}

/** Real [FavoritePlayTransport] over UPnP/SOAP. */
class NetworkFavoritePlayer(
    private val gateway: SonosGateway,
    private val httpClient: SonosHttpClient = JavaNetSonosHttpClient()
) : FavoritePlayTransport {

    // Discovery is slow (SSDP); memoize for the lifetime of one play() call.
    private val discovered by lazy { gateway.discoverPlayers() }

    override fun targetPlayers(rooms: Set<String>): List<SonosPlayer> =
        SonosTopology.playersForRooms(discovered, rooms)

    override fun favorites(): List<SonosFavorite> {
        val player = discovered.firstOrNull { it.services.contentDirectoryControlUrl != null }
            ?: return emptyList()
        val response = httpClient.soap(
            url = player.baseUrl + player.services.contentDirectoryControlUrl,
            soapAction = SonosSoap.contentDirectorySoapAction("Browse"),
            envelope = SonosSoap.browseFavoritesEnvelope()
        )
        return SonosSoap.parseFavorites(response)
    }

    override fun group(lead: SonosPlayer, members: List<SonosPlayer>) {
        // Detach the lead into its own group, then pull each member in.
        avPost(lead, "BecomeCoordinatorOfStandaloneGroup",
            SonosSoap.avTransportEnvelope("BecomeCoordinatorOfStandaloneGroup"))
        val joinUri = "x-rincon:" + lead.uuid.removePrefix("uuid:").trim()
        members.forEach { member ->
            runCatching {
                avPost(member, "SetAVTransportURI", SonosSoap.setAvTransportUriEnvelope(joinUri, ""))
            }.onFailure { Log.w(TAG, "Failed to group ${member.roomName}", it) }
        }
    }

    override fun play(lead: SonosPlayer, favorite: SonosFavorite) {
        avPost(lead, "SetAVTransportURI", SonosSoap.setAvTransportUriEnvelope(favorite.uri, favorite.metadata))
        avPost(lead, "Play", SonosSoap.avTransportEnvelope("Play", "<Speed>1</Speed>"))
        Log.i(TAG, "Playing '${favorite.title}' from ${lead.roomName}")
    }

    private fun avPost(player: SonosPlayer, action: String, envelope: String): String =
        httpClient.soap(
            url = player.baseUrl + player.services.avTransportControlUrl,
            soapAction = SonosSoap.avTransportSoapAction(action),
            envelope = envelope
        )

    companion object {
        private const val TAG = "SonosWidget"
    }
}
