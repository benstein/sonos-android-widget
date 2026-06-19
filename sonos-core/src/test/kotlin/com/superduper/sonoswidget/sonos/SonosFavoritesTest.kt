package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosFavoritesTest {

    @Test
    fun browseEnvelopeTargetsFavoritesContainer() {
        val envelope = SonosSoap.browseFavoritesEnvelope()
        assertTrue(envelope.contains("<ObjectID>FV:2</ObjectID>"))
        assertTrue(envelope.contains("<u:Browse"))
        assertTrue(envelope.contains("BrowseDirectChildren"))
    }

    @Test
    fun parsesTitleUriAndMetadataFromDoublyEscapedResult() {
        // The DIDL as it looks after one level of unescaping (what <Result> decodes to).
        val innerDidl =
            """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
                """xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/">""" +
                """<item id="FV:2/1"><dc:title>David's Song</dc:title>""" +
                """<res>x-sonos-spotify:spotify%3atrack%3aABC?sid=9&amp;flags=8224&amp;sn=12</res>""" +
                """<r:resMD>&lt;DIDL-Lite&gt;meta&lt;/DIDL-Lite&gt;</r:resMD></item></DIDL-Lite>"""
        // The SOAP <Result> escapes that DIDL once more.
        val response = browseResponse(SonosSoap.escapeXml(innerDidl))

        val favorites = SonosSoap.parseFavorites(response)

        assertEquals(1, favorites.size)
        assertEquals("David's Song", favorites[0].title)
        assertEquals("x-sonos-spotify:spotify%3atrack%3aABC?sid=9&flags=8224&sn=12", favorites[0].uri)
        assertEquals("<DIDL-Lite>meta</DIDL-Lite>", favorites[0].metadata)
    }

    @Test
    fun parsesMultipleFavorites() {
        val innerDidl =
            """<DIDL-Lite><item><dc:title>One</dc:title><res>x-one</res><r:resMD>m1</r:resMD></item>""" +
                """<item><dc:title>Two</dc:title><res>x-two</res><r:resMD>m2</r:resMD></item></DIDL-Lite>"""
        val favorites = SonosSoap.parseFavorites(browseResponse(SonosSoap.escapeXml(innerDidl)))
        assertEquals(listOf("One", "Two"), favorites.map { it.title })
        assertEquals(listOf("x-one", "x-two"), favorites.map { it.uri })
    }

    private fun browseResponse(escapedResult: String): String =
        """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
              <Result>$escapedResult</Result>
              <NumberReturned>1</NumberReturned>
              <TotalMatches>1</TotalMatches>
            </u:BrowseResponse>
          </s:Body>
        </s:Envelope>
        """.trimIndent()
}
