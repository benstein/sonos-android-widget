package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosSoapAnnounceTest {

    @Test
    fun setVolumeEnvelopeClampsOutOfRange() {
        assertTrue(SonosSoap.setVolumeEnvelope(150).contains("<DesiredVolume>100</DesiredVolume>"))
        assertTrue(SonosSoap.setVolumeEnvelope(-5).contains("<DesiredVolume>0</DesiredVolume>"))
        assertTrue(SonosSoap.setVolumeEnvelope(42).contains("<DesiredVolume>42</DesiredVolume>"))
    }

    @Test
    fun setAvTransportUriEnvelopeEscapesUriAndMetadata() {
        val envelope = SonosSoap.setAvTransportUriEnvelope(
            uri = "http://host/clip.wav?a=1&b=2",
            metadata = "<DIDL-Lite>item</DIDL-Lite>"
        )
        assertTrue(envelope.contains("clip.wav?a=1&amp;b=2"))
        assertTrue(envelope.contains("&lt;DIDL-Lite&gt;item&lt;/DIDL-Lite&gt;"))
        // The escaped payload must not leak raw angle brackets into the envelope body.
        assertTrue(envelope.contains("<CurrentURIMetaData>&lt;DIDL-Lite&gt;"))
    }

    @Test
    fun escapeXmlHandlesAllEntities() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", SonosSoap.escapeXml("&<>\"'"))
        assertEquals("plain text", SonosSoap.escapeXml("plain text"))
    }

    @Test
    fun parseVolumeReadsCurrentVolume() {
        val response = soapBody("<CurrentVolume>27</CurrentVolume>")
        assertEquals(27, SonosSoap.parseVolume(response))
    }

    @Test
    fun parseMediaInfoReadsUriAndMetadata() {
        val response = soapBody(
            "<CurrentURI>x-rincon-queue:RINCON_ABC#0</CurrentURI>" +
                "<CurrentURIMetaData>&lt;DIDL&gt;meta&lt;/DIDL&gt;</CurrentURIMetaData>"
        )
        assertEquals("x-rincon-queue:RINCON_ABC#0", SonosSoap.parseCurrentUri(response))
        assertEquals("<DIDL>meta</DIDL>", SonosSoap.parseCurrentUriMetaData(response))
    }

    @Test
    fun parsePositionInfoReadsTrackAndRelTime() {
        val response = soapBody("<Track>3</Track><RelTime>0:01:23</RelTime>")
        assertEquals(3, SonosSoap.parseTrackNumber(response))
        assertEquals("0:01:23", SonosSoap.parseRelTime(response))
    }

    @Test
    fun parseRelTimeTreatsBlankAsNull() {
        assertNull(SonosSoap.parseRelTime(soapBody("<RelTime></RelTime>")))
    }

    private fun soapBody(inner: String): String =
        """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <u:Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              $inner
            </u:Response>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}
