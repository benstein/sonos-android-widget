package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosXmlTest {
    @Test
    fun parsesDeviceDescription() {
        val xml = """
            <root>
              <device>
                <roomName>Dining Room</roomName>
                <UDN>uuid:RINCON_12345678901400</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/MediaRenderer/AVTransport/Control</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:ZoneGroupTopology:1</serviceType>
                    <controlURL>/ZoneGroupTopology/Control</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val player = SonosXml.parseDeviceDescription(xml, "http://192.168.1.20:1400/xml/device_description.xml")

        assertEquals("Dining Room", player.roomName)
        assertEquals("uuid:RINCON_12345678901400", player.uuid)
        assertEquals("http://192.168.1.20:1400", player.baseUrl)
        assertEquals("/MediaRenderer/AVTransport/Control", player.services.avTransportControlUrl)
        assertEquals("/ZoneGroupTopology/Control", player.services.zoneGroupTopologyControlUrl)
    }

    @Test
    fun parsesTrackMetadataAndRelativeArtwork() {
        val didl = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item>
                <dc:title>This Must Be the Place</dc:title>
                <dc:creator>Talking Heads</dc:creator>
                <upnp:albumArtURI>/getaa?s=1&amp;u=x-sonos-spotify:spotify%3atrack%3a123</upnp:albumArtURI>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val track = SonosXml.parseTrackMetadata(didl, "http://192.168.1.20:1400")

        assertEquals("This Must Be the Place", track.title)
        assertEquals("Talking Heads", track.artist)
        assertEquals("http://192.168.1.20:1400/getaa?s=1&u=x-sonos-spotify:spotify%3atrack%3a123", track.artworkUrl)
    }

    @Test
    fun parsesTransportActions() {
        val actions = SonosXml.parseTransportActions("Play, Pause, Next, Previous, X_DLNA_SeekTime")

        assertTrue(actions.canPlay)
        assertTrue(actions.canPause)
        assertTrue(actions.canNext)
        assertTrue(actions.canPrevious)
    }
}
