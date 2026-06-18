package com.superduper.sonoswidget.sonos

object SonosSoap {
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val ZONE_GROUP_TOPOLOGY = "urn:schemas-upnp-org:service:ZoneGroupTopology:1"

    fun avTransportSoapAction(action: String): String = "\"$AV_TRANSPORT#$action\""

    fun zoneGroupTopologySoapAction(action: String): String = "\"$ZONE_GROUP_TOPOLOGY#$action\""

    fun avTransportEnvelope(action: String, body: String = ""): String {
        return envelope(
            action = action,
            service = AV_TRANSPORT,
            body = "<InstanceID>0</InstanceID>$body"
        )
    }

    fun zoneGroupTopologyEnvelope(action: String, body: String = ""): String {
        return envelope(action = action, service = ZONE_GROUP_TOPOLOGY, body = body)
    }

    fun parsePlaybackState(responseXml: String): PlaybackState {
        return when (SonosXml.parseSoapValue(responseXml, "CurrentTransportState")) {
            "PLAYING" -> PlaybackState.PLAYING
            "PAUSED_PLAYBACK" -> PlaybackState.PAUSED
            "STOPPED" -> PlaybackState.STOPPED
            else -> PlaybackState.UNKNOWN
        }
    }

    fun parseTrack(responseXml: String, baseUrl: String): SonosTrack {
        return SonosXml.parseTrackMetadata(
            metadataXml = SonosXml.parseSoapValue(responseXml, "TrackMetaData"),
            baseUrl = baseUrl
        )
    }

    fun parseTransportActions(responseXml: String): SonosTransportActions {
        return SonosXml.parseTransportActions(SonosXml.parseSoapValue(responseXml, "Actions"))
    }

    fun parseZoneGroupState(responseXml: String): List<ZoneGroupMember> {
        val state = SonosXml.parseSoapValue(responseXml, "ZoneGroupState") ?: return emptyList()
        return SonosXml.parseZoneGroupMembers(state)
    }

    private fun envelope(action: String, service: String, body: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="$service">
                  $body
                </u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}
