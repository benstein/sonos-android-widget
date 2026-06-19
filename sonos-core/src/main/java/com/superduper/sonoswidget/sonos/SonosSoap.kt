package com.superduper.sonoswidget.sonos

object SonosSoap {
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val ZONE_GROUP_TOPOLOGY = "urn:schemas-upnp-org:service:ZoneGroupTopology:1"
    private const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"

    fun avTransportSoapAction(action: String): String = "\"$AV_TRANSPORT#$action\""

    fun zoneGroupTopologySoapAction(action: String): String = "\"$ZONE_GROUP_TOPOLOGY#$action\""

    fun renderingControlSoapAction(action: String): String = "\"$RENDERING_CONTROL#$action\""

    fun contentDirectorySoapAction(action: String): String = "\"$CONTENT_DIRECTORY#$action\""

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

    fun renderingControlEnvelope(action: String, body: String = ""): String {
        return envelope(
            action = action,
            service = RENDERING_CONTROL,
            body = "<InstanceID>0</InstanceID><Channel>Master</Channel>$body"
        )
    }

    /** ContentDirectory Browse of the FV:2 (Sonos Favorites) container. */
    fun browseFavoritesEnvelope(): String {
        return envelope(
            action = "Browse",
            service = CONTENT_DIRECTORY,
            body = "<ObjectID>FV:2</ObjectID>" +
                "<BrowseFlag>BrowseDirectChildren</BrowseFlag>" +
                "<Filter>*</Filter>" +
                "<StartingIndex>0</StartingIndex>" +
                "<RequestedCount>200</RequestedCount>" +
                "<SortCriteria></SortCriteria>"
        )
    }

    fun parseFavorites(browseResponseXml: String): List<SonosFavorite> {
        val result = SonosXml.parseSoapValue(browseResponseXml, "Result") ?: return emptyList()
        return SonosXml.parseFavorites(result)
    }

    /** AVTransport SetAVTransportURI. URI and DIDL metadata are escaped for XML. */
    fun setAvTransportUriEnvelope(uri: String, metadata: String): String {
        return avTransportEnvelope(
            action = "SetAVTransportURI",
            body = "<CurrentURI>${escapeXml(uri)}</CurrentURI>" +
                "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>"
        )
    }

    fun setVolumeEnvelope(volume: Int): String {
        return renderingControlEnvelope(
            action = "SetVolume",
            body = "<DesiredVolume>${volume.coerceIn(0, 100)}</DesiredVolume>"
        )
    }

    fun seekEnvelope(unit: String, target: String): String {
        return avTransportEnvelope(
            action = "Seek",
            body = "<Unit>$unit</Unit><Target>${escapeXml(target)}</Target>"
        )
    }

    fun parseVolume(responseXml: String): Int? =
        SonosXml.parseSoapValue(responseXml, "CurrentVolume")?.toIntOrNull()

    fun parseCurrentUri(responseXml: String): String? =
        SonosXml.parseSoapValue(responseXml, "CurrentURI")

    fun parseCurrentUriMetaData(responseXml: String): String? =
        SonosXml.parseSoapValue(responseXml, "CurrentURIMetaData")

    /** GetPositionInfo reports the in-queue track index in <Track>. */
    fun parseTrackNumber(responseXml: String): Int? =
        SonosXml.parseSoapValue(responseXml, "Track")?.toIntOrNull()

    fun parseRelTime(responseXml: String): String? =
        SonosXml.parseSoapValue(responseXml, "RelTime")?.takeIf { it.isNotBlank() }

    fun escapeXml(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
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
