package com.superduper.sonoswidget.sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class SonosDiscovery(
    private val context: Context,
    private val httpClient: SonosHttpClient = JavaNetSonosHttpClient()
) : SonosGateway {
    override fun discoverPlayers(): List<SonosPlayer> {
        val locations = discoverLocations()
        Log.i(TAG, "SSDP locations=${locations.size}: $locations")
        return locations
            .mapNotNull { location ->
                val player = runCatching {
                    SonosXml.parseDeviceDescription(httpClient.get(location), location)
                }.onFailure { error ->
                    if (error is IllegalStateException && error.message == "Missing AVTransport service") {
                        Log.i(TAG, "Ignoring non-player device at $location")
                    } else {
                        Log.w(TAG, "Failed to load Sonos device description at $location", error)
                    }
                }.getOrNull()
                if (player != null) {
                    Log.i(TAG, "Discovered player room=${player.roomName} uuid=${player.uuid} baseUrl=${player.baseUrl}")
                }
                player
            }
            .distinctBy { it.uuid }
    }

    override fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember> {
        val controlUrl = player.services.zoneGroupTopologyControlUrl ?: return emptyList()
        Log.i(TAG, "Fetching zone group topology from room=${player.roomName} url=${player.baseUrl + controlUrl}")
        val response = httpClient.soap(
            url = player.baseUrl + controlUrl,
            soapAction = SonosSoap.zoneGroupTopologySoapAction("GetZoneGroupState"),
            envelope = SonosSoap.zoneGroupTopologyEnvelope("GetZoneGroupState")
        )
        return SonosSoap.parseZoneGroupState(response).also { members ->
            Log.i(TAG, "Zone group members=${members.size}: $members")
        }
    }

    override fun playback(player: SonosPlayer): SonosPlayback {
        val transportUrl = player.baseUrl + player.services.avTransportControlUrl
        Log.i(TAG, "Fetching playback from room=${player.roomName} url=$transportUrl")
        val state = SonosSoap.parsePlaybackState(
            httpClient.soap(
                url = transportUrl,
                soapAction = SonosSoap.avTransportSoapAction("GetTransportInfo"),
                envelope = SonosSoap.avTransportEnvelope("GetTransportInfo")
            )
        )
        val track = SonosSoap.parseTrack(
            responseXml = httpClient.soap(
                url = transportUrl,
                soapAction = SonosSoap.avTransportSoapAction("GetPositionInfo"),
                envelope = SonosSoap.avTransportEnvelope("GetPositionInfo")
            ),
            baseUrl = player.baseUrl
        )
        val actions = SonosSoap.parseTransportActions(
            httpClient.soap(
                url = transportUrl,
                soapAction = SonosSoap.avTransportSoapAction("GetCurrentTransportActions"),
                envelope = SonosSoap.avTransportEnvelope("GetCurrentTransportActions")
            )
        )
        return SonosPlayback(player.roomName, state, track, actions).also {
            Log.i(TAG, "Playback room=${it.roomName} state=${it.state} title=${it.track.title} actions=${it.actions}")
        }
    }

    override fun play(player: SonosPlayer) = sendTransport(player, "Play", "<Speed>1</Speed>")

    override fun pause(player: SonosPlayer) = sendTransport(player, "Pause")

    override fun next(player: SonosPlayer) = sendTransport(player, "Next")

    override fun previous(player: SonosPlayer) = sendTransport(player, "Previous")

    private fun sendTransport(player: SonosPlayer, action: String, body: String = "") {
        httpClient.soap(
            url = player.baseUrl + player.services.avTransportControlUrl,
            soapAction = SonosSoap.avTransportSoapAction(action),
            envelope = SonosSoap.avTransportEnvelope(action, body)
        )
    }

    private fun discoverLocations(): List<String> {
        Log.i(TAG, "Starting SSDP discovery")
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("sonos-widget-ssdp").apply {
            setReferenceCounted(false)
        }
        val socket = DatagramSocket()
        val locations = linkedSetOf<String>()

        try {
            lock.acquire()
            socket.soTimeout = 1_500
            val query = listOf(
                "M-SEARCH * HTTP/1.1",
                "HOST: 239.255.255.250:1900",
                "MAN: \"ssdp:discover\"",
                "MX: 1",
                "ST: urn:schemas-upnp-org:device:ZonePlayer:1",
                "",
                ""
            ).joinToString("\r\n")
            val bytes = query.toByteArray(Charsets.UTF_8)
            socket.send(
                DatagramPacket(
                    bytes,
                    bytes.size,
                    InetAddress.getByName("239.255.255.250"),
                    1900
                )
            )

            while (true) {
                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                response.lineSequence()
                    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.let { locations += it }
            }
        } catch (_: SocketTimeoutException) {
            Log.i(TAG, "SSDP discovery timed out with ${locations.size} location(s)")
            return locations.toList()
        } catch (error: Exception) {
            Log.w(TAG, "SSDP discovery failed", error)
            return locations.toList()
        } finally {
            socket.close()
            if (lock.isHeld) lock.release()
        }
    }

    companion object {
        private const val TAG = "SonosWidget"
    }
}
