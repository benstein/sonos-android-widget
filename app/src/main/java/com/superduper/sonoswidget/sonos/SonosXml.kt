package com.superduper.sonoswidget.sonos

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

object SonosXml {
    fun parseDeviceDescription(xml: String, locationUrl: String): SonosPlayer {
        val root = parse(xml).documentElement
        val roomName = firstText(root, "roomName")
            ?: firstText(root, "friendlyName")
            ?: "Unknown room"
        val uuid = firstText(root, "UDN") ?: error("Missing UDN")
        val avTransport = firstServiceControlUrl(root, "AVTransport")
            ?: error("Missing AVTransport service")
        val topology = firstServiceControlUrl(root, "ZoneGroupTopology")

        return SonosPlayer(
            roomName = roomName,
            uuid = uuid,
            baseUrl = baseUrl(locationUrl),
            services = SonosServices(
                avTransportControlUrl = avTransport,
                zoneGroupTopologyControlUrl = topology
            )
        )
    }

    fun parseTrackMetadata(metadataXml: String?, baseUrl: String): SonosTrack {
        if (metadataXml.isNullOrBlank() || metadataXml == "NOT_IMPLEMENTED") {
            return SonosTrack(title = null, artist = null, artworkUrl = null)
        }

        val root = parse(metadataXml).documentElement
        return SonosTrack(
            title = firstText(root, "title"),
            artist = firstText(root, "creator") ?: firstText(root, "artist"),
            artworkUrl = firstText(root, "albumArtURI")?.let { resolveUrl(baseUrl, it) }
        )
    }

    fun parseTransportActions(actions: String?): SonosTransportActions {
        val parts = actions.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return SonosTransportActions(
            canPlay = "Play" in parts,
            canPause = "Pause" in parts,
            canNext = "Next" in parts,
            canPrevious = "Previous" in parts
        )
    }

    fun parseZoneGroupMembers(zoneGroupStateXml: String): List<ZoneGroupMember> {
        val root = parse(zoneGroupStateXml).documentElement
        return elements(root, "ZoneGroup").flatMap { group ->
            val coordinatorUuid = group.getAttribute("Coordinator")
            elements(group, "ZoneGroupMember").map { member ->
                ZoneGroupMember(
                    uuid = member.getAttribute("UUID"),
                    roomName = member.getAttribute("ZoneName"),
                    coordinatorUuid = coordinatorUuid
                )
            }
        }
    }

    fun parseSoapValue(xml: String, tagName: String): String? {
        return firstText(parse(xml).documentElement, tagName)
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun firstServiceControlUrl(root: Element, serviceName: String): String? {
        return elements(root, "service")
            .firstOrNull { service ->
                firstText(service, "serviceType")?.contains(serviceName) == true
            }
            ?.let { firstText(it, "controlURL") }
    }

    private fun firstText(node: Node, wantedLocalName: String): String? {
        if (node.nodeType == Node.ELEMENT_NODE && node.localOrNodeName() == wantedLocalName) {
            return node.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }

        val children = node.childNodes
        for (index in 0 until children.length) {
            val found = firstText(children.item(index), wantedLocalName)
            if (found != null) return found
        }
        return null
    }

    private fun elements(node: Node, wantedLocalName: String): List<Element> {
        val output = mutableListOf<Element>()
        if (node.nodeType == Node.ELEMENT_NODE && node.localOrNodeName() == wantedLocalName) {
            output += node as Element
        }

        val children = node.childNodes
        for (index in 0 until children.length) {
            output += elements(children.item(index), wantedLocalName)
        }
        return output
    }

    private fun Node.localOrNodeName(): String {
        return localName ?: nodeName.substringAfter(':')
    }

    private fun baseUrl(locationUrl: String): String {
        val uri = URI(locationUrl)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        return if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            candidate
        } else {
            URI(baseUrl).resolve(candidate).toString()
        }
    }
}
