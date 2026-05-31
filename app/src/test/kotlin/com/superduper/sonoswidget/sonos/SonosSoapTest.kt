package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosSoapTest {
    @Test
    fun playEnvelopeIncludesInstanceAndSpeed() {
        val envelope = SonosSoap.avTransportEnvelope(
            action = "Play",
            body = "<Speed>1</Speed>"
        )

        assertTrue(envelope.contains("<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"))
        assertTrue(envelope.contains("<InstanceID>0</InstanceID>"))
        assertTrue(envelope.contains("<Speed>1</Speed>"))
    }

    @Test
    fun parsesTransportInfo() {
        val response = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <CurrentTransportState>PLAYING</CurrentTransportState>
                </u:GetTransportInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertEquals(PlaybackState.PLAYING, SonosSoap.parsePlaybackState(response))
    }

    @Test
    fun buildsSoapActionHeader() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"",
            SonosSoap.avTransportSoapAction("Pause")
        )
    }
}
