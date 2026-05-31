# Sonos Android Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a sideloadable Android widget that controls Ben's Sonos group over the local Wi-Fi network.

**Architecture:** A small Kotlin Android app owns settings, local Sonos discovery/control, and a RemoteViews app widget. The Sonos layer hides SSDP, SOAP, group coordinator lookup, metadata parsing, and transport commands behind `SonosRepository`, so widget and activity code stay simple.

**Tech Stack:** Kotlin, Android Gradle Plugin 8.13.0, Gradle 8.13, Android SDK 36, RemoteViews AppWidget APIs, WorkManager 2.11.0, JUnit 4, Java `HttpURLConnection`, Java XML parsers.

---

## File Structure

- Create `settings.gradle.kts`: Gradle plugin repositories and `:app` module include.
- Create `build.gradle.kts`: root plugin declarations.
- Create `gradle.properties`: AndroidX and Kotlin build flags.
- Create `app/build.gradle.kts`: Android app config, dependencies, and unit test setup.
- Create `app/src/main/AndroidManifest.xml`: permissions, activity, widget provider, action receiver, WorkManager metadata defaults.
- Create `app/src/main/res/values/strings.xml`: app and widget labels.
- Create `app/src/main/res/values/styles.xml`: minimal app theme.
- Create `app/src/main/res/xml/sonos_widget_info.xml`: 4x1 widget sizing and resize behavior.
- Create `app/src/main/res/drawable/widget_background.xml`, `widget_button.xml`, `widget_button_disabled.xml`, `artwork_fallback.xml`: widget backgrounds.
- Create `app/src/main/res/layout/widget_sonos_wide.xml`: 4x1 widget layout with artwork.
- Create `app/src/main/res/layout/widget_sonos_compact.xml`: narrow widget layout without artwork.
- Create `app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`: room picker and current status.
- Create `app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt`: widget lifecycle entry point.
- Create `app/src/main/java/com/superduper/sonoswidget/WidgetActionReceiver.kt`: play/pause/previous/next/settings intents.
- Create `app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt`: periodic and tap-triggered refresh.
- Create `app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt`: RemoteViews rendering.
- Create `app/src/main/java/com/superduper/sonoswidget/widget/WidgetState.kt`: display model for widget states.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosModels.kt`: domain data classes.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosXml.kt`: XML parsing helpers.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosSoap.kt`: SOAP envelopes and response parsing.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt`: HTTP client interface and implementation.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosDiscovery.kt`: SSDP discovery and device-description loading.
- Create `app/src/main/java/com/superduper/sonoswidget/sonos/SonosRepository.kt`: orchestration for state and commands.
- Create `app/src/main/java/com/superduper/sonoswidget/storage/SonosPrefs.kt`: SharedPreferences wrapper.
- Create `app/src/test/kotlin/com/superduper/sonoswidget/BuildConfigSmokeTest.kt`: verifies scaffold.
- Create `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosXmlTest.kt`: parser coverage.
- Create `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosSoapTest.kt`: SOAP envelope and fake-server coverage.
- Create `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosRepositoryTest.kt`: coordinator and action behavior with fakes.
- Create `app/src/test/kotlin/com/superduper/sonoswidget/widget/WidgetStateTest.kt`: widget display-state coverage.

## Task 1: Scaffold Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`
- Create: `app/src/test/kotlin/com/superduper/sonoswidget/BuildConfigSmokeTest.kt`

- [ ] **Step 1: Write the failing smoke test**

```kotlin
package com.superduper.sonoswidget

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigSmokeTest {
    @Test
    fun exposesExpectedApplicationId() {
        assertEquals("com.superduper.sonoswidget", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails before the scaffold exists**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.BuildConfigSmokeTest`

Expected: FAIL because `./gradlew` or the `:app` project does not exist yet.

- [ ] **Step 3: Create the Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.13 --distribution-type bin`

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties` are created.

- [ ] **Step 4: Create Gradle settings and root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SonosWidget"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}
```

`gradle.properties`:

```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
```

- [ ] **Step 5: Create the app build file**

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superduper.sonoswidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.superduper.sonoswidget"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 6: Create the minimal manifest, strings, and activity**

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission
        android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Sonos Widget</string>
    <string name="widget_name">Sonos Controller</string>
</resources>
```

`app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:colorAccent">#111923</item>
    </style>
</resources>
```

`app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`:

```kotlin
package com.superduper.sonoswidget

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Sonos Widget"
            textSize = 24f
            setPadding(48, 48, 48, 48)
        })
    }
}
```

- [ ] **Step 7: Run the smoke test**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.BuildConfigSmokeTest`

Expected: PASS.

- [ ] **Step 8: Build the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: PASS and APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle app
git commit -m "Scaffold Android widget app"
```

## Task 2: Add Sonos Domain Models and XML Parsing

**Files:**
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosModels.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosXml.kt`
- Create: `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosXmlTest.kt`

- [ ] **Step 1: Write failing XML parser tests**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosXmlTest`

Expected: FAIL with unresolved `SonosXml`.

- [ ] **Step 3: Create domain models**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosModels.kt`:

```kotlin
package com.superduper.sonoswidget.sonos

data class SonosServices(
    val avTransportControlUrl: String,
    val zoneGroupTopologyControlUrl: String?
)

data class SonosPlayer(
    val roomName: String,
    val uuid: String,
    val baseUrl: String,
    val services: SonosServices
)

data class SonosTrack(
    val title: String?,
    val artist: String?,
    val artworkUrl: String?
)

data class SonosTransportActions(
    val canPlay: Boolean,
    val canPause: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean
) {
    companion object {
        val none = SonosTransportActions(
            canPlay = false,
            canPause = false,
            canNext = false,
            canPrevious = false
        )
    }
}

enum class PlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
    UNKNOWN
}

data class SonosPlayback(
    val roomName: String,
    val state: PlaybackState,
    val track: SonosTrack,
    val actions: SonosTransportActions
)

data class ZoneGroupMember(
    val uuid: String,
    val roomName: String,
    val coordinatorUuid: String
)
```

- [ ] **Step 4: Implement XML parser helpers**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosXml.kt`:

```kotlin
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
            return SonosTrack(null, null, null)
        }
        val root = parse(metadataXml).documentElement
        val title = firstText(root, "title")
        val artist = firstText(root, "creator")
        val art = firstText(root, "albumArtURI")?.let { resolveUrl(baseUrl, it) }
        return SonosTrack(title = title, artist = artist, artworkUrl = art)
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
        val groups = elements(root, "ZoneGroup")
        return groups.flatMap { group ->
            val coordinator = group.getAttribute("Coordinator")
            elements(group, "ZoneGroupMember").map { member ->
                ZoneGroupMember(
                    uuid = member.getAttribute("UUID"),
                    roomName = member.getAttribute("ZoneName"),
                    coordinatorUuid = coordinator
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
        return "${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 1400}"
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        return if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            candidate
        } else {
            URI(baseUrl).resolve(candidate).toString()
        }
    }
}
```

- [ ] **Step 5: Run XML parser tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosXmlTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/sonos app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosXmlTest.kt
git commit -m "Add Sonos XML parsing"
```

## Task 3: Add SOAP Client and Transport Commands

**Files:**
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosSoap.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt`
- Create: `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosSoapTest.kt`

- [ ] **Step 1: Write failing SOAP tests**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosSoapTest`

Expected: FAIL with unresolved `SonosSoap`.

- [ ] **Step 3: Implement SOAP helpers**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosSoap.kt`:

```kotlin
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
```

- [ ] **Step 4: Implement HTTP client**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt`:

```kotlin
package com.superduper.sonoswidget.sonos

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface SonosHttpClient {
    fun get(url: String): String
    fun soap(url: String, soapAction: String, envelope: String): String
}

class JavaNetSonosHttpClient(
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000
) : SonosHttpClient {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        return connection.useResponseText()
    }

    override fun soap(url: String, soapAction: String, envelope: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", soapAction)
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(envelope)
        }
        return connection.useResponseText()
    }

    private fun HttpURLConnection.useResponseText(): String {
        return try {
            val stream = if (responseCode in 200..299) inputStream else errorStream
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            disconnect()
        }
    }
}
```

- [ ] **Step 5: Run SOAP tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosSoapTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/sonos/SonosSoap.kt app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosSoapTest.kt
git commit -m "Add Sonos SOAP transport helpers"
```

## Task 4: Add Discovery, Preferences, and Repository

**Files:**
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosDiscovery.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosRepository.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/storage/SonosPrefs.kt`
- Create: `app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

```kotlin
package com.superduper.sonoswidget.sonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonosRepositoryTest {
    @Test
    fun resolvesSelectedRoomToCoordinatorBeforePlayPause() {
        val dining = SonosPlayer(
            roomName = "Dining Room",
            uuid = "uuid:RINCON_DINING",
            baseUrl = "http://192.168.1.20:1400",
            services = SonosServices("/MediaRenderer/AVTransport/Control", "/ZoneGroupTopology/Control")
        )
        val kitchen = dining.copy(
            roomName = "Kitchen",
            uuid = "uuid:RINCON_KITCHEN",
            baseUrl = "http://192.168.1.21:1400"
        )
        val fake = FakeSonosGateway(
            players = listOf(dining, kitchen),
            zoneMembers = listOf(
                ZoneGroupMember("uuid:RINCON_DINING", "Dining Room", "uuid:RINCON_KITCHEN"),
                ZoneGroupMember("uuid:RINCON_KITCHEN", "Kitchen", "uuid:RINCON_KITCHEN")
            ),
            playbackByUuid = mapOf(
                "uuid:RINCON_KITCHEN" to SonosPlayback(
                    roomName = "Kitchen",
                    state = PlaybackState.PAUSED,
                    track = SonosTrack("Song", "Artist", null),
                    actions = SonosTransportActions(canPlay = true, canPause = false, canNext = true, canPrevious = true)
                )
            )
        )

        val repository = SonosRepository(fake)
        repository.togglePlayPause("Dining Room")

        assertEquals(listOf("Play:uuid:RINCON_KITCHEN"), fake.commands)
    }

    @Test
    fun reportsUnavailableWhenRoomIsMissing() {
        val repository = SonosRepository(FakeSonosGateway(players = emptyList()))

        val result = repository.currentPlayback("Dining Room")

        assertTrue(result is SonosResult.Unavailable)
    }

    private class FakeSonosGateway(
        private val players: List<SonosPlayer>,
        private val zoneMembers: List<ZoneGroupMember> = emptyList(),
        private val playbackByUuid: Map<String, SonosPlayback> = emptyMap()
    ) : SonosGateway {
        val commands = mutableListOf<String>()

        override fun discoverPlayers(): List<SonosPlayer> = players
        override fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember> = zoneMembers
        override fun playback(player: SonosPlayer): SonosPlayback =
            playbackByUuid.getValue(player.uuid)

        override fun play(player: SonosPlayer) {
            commands += "Play:${player.uuid}"
        }

        override fun pause(player: SonosPlayer) {
            commands += "Pause:${player.uuid}"
        }

        override fun next(player: SonosPlayer) {
            commands += "Next:${player.uuid}"
        }

        override fun previous(player: SonosPlayer) {
            commands += "Previous:${player.uuid}"
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosRepositoryTest`

Expected: FAIL with unresolved `SonosRepository`, `SonosGateway`, and `SonosResult`.

- [ ] **Step 3: Implement repository and gateway**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosRepository.kt`:

```kotlin
package com.superduper.sonoswidget.sonos

sealed class SonosResult {
    data class Available(val playback: SonosPlayback) : SonosResult()
    data class Unavailable(val message: String) : SonosResult()
}

interface SonosGateway {
    fun discoverPlayers(): List<SonosPlayer>
    fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember>
    fun playback(player: SonosPlayer): SonosPlayback
    fun play(player: SonosPlayer)
    fun pause(player: SonosPlayer)
    fun next(player: SonosPlayer)
    fun previous(player: SonosPlayer)
}

class SonosRepository(
    private val gateway: SonosGateway
) {
    fun rooms(): List<String> = gateway.discoverPlayers().map { it.roomName }.distinct().sorted()

    fun currentPlayback(roomName: String?): SonosResult {
        val selected = selectedPlayer(roomName) ?: return SonosResult.Unavailable("Choose room")
        val coordinator = coordinatorFor(selected)
        return try {
            SonosResult.Available(gateway.playback(coordinator).copy(roomName = selected.roomName))
        } catch (_: Exception) {
            SonosResult.Unavailable("Sonos unavailable")
        }
    }

    fun togglePlayPause(roomName: String?) {
        val selected = selectedPlayer(roomName) ?: return
        val coordinator = coordinatorFor(selected)
        val playback = gateway.playback(coordinator)
        if (playback.state == PlaybackState.PLAYING) {
            gateway.pause(coordinator)
        } else {
            gateway.play(coordinator)
        }
    }

    fun next(roomName: String?) {
        val selected = selectedPlayer(roomName) ?: return
        val coordinator = coordinatorFor(selected)
        val playback = gateway.playback(coordinator)
        if (playback.actions.canNext) gateway.next(coordinator)
    }

    fun previous(roomName: String?) {
        val selected = selectedPlayer(roomName) ?: return
        val coordinator = coordinatorFor(selected)
        val playback = gateway.playback(coordinator)
        if (playback.actions.canPrevious) gateway.previous(coordinator)
    }

    private fun selectedPlayer(roomName: String?): SonosPlayer? {
        if (roomName.isNullOrBlank()) return null
        return gateway.discoverPlayers().firstOrNull { it.roomName == roomName }
    }

    private fun coordinatorFor(player: SonosPlayer): SonosPlayer {
        val members = gateway.zoneGroupMembers(player)
        val coordinatorUuid = members.firstOrNull { it.uuid == player.uuid }?.coordinatorUuid ?: player.uuid
        return gateway.discoverPlayers().firstOrNull { it.uuid == coordinatorUuid } ?: player
    }
}
```

- [ ] **Step 4: Implement Android gateway discovery and SOAP access**

`app/src/main/java/com/superduper/sonoswidget/sonos/SonosDiscovery.kt`:

```kotlin
package com.superduper.sonoswidget.sonos

import android.content.Context
import android.net.wifi.WifiManager
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
        return locations.mapNotNull { location ->
            runCatching { SonosXml.parseDeviceDescription(httpClient.get(location), location) }.getOrNull()
        }.distinctBy { it.uuid }
    }

    override fun zoneGroupMembers(player: SonosPlayer): List<ZoneGroupMember> {
        val controlUrl = player.services.zoneGroupTopologyControlUrl ?: return emptyList()
        val response = httpClient.soap(
            url = player.baseUrl + controlUrl,
            soapAction = SonosSoap.zoneGroupTopologySoapAction("GetZoneGroupState"),
            envelope = SonosSoap.zoneGroupTopologyEnvelope("GetZoneGroupState")
        )
        return SonosSoap.parseZoneGroupState(response)
    }

    override fun playback(player: SonosPlayer): SonosPlayback {
        val transportUrl = player.baseUrl + player.services.avTransportControlUrl
        val state = SonosSoap.parsePlaybackState(httpClient.soap(
            url = transportUrl,
            soapAction = SonosSoap.avTransportSoapAction("GetTransportInfo"),
            envelope = SonosSoap.avTransportEnvelope("GetTransportInfo")
        ))
        val track = SonosSoap.parseTrack(httpClient.soap(
            url = transportUrl,
            soapAction = SonosSoap.avTransportSoapAction("GetPositionInfo"),
            envelope = SonosSoap.avTransportEnvelope("GetPositionInfo")
        ), player.baseUrl)
        val actions = SonosSoap.parseTransportActions(httpClient.soap(
            url = transportUrl,
            soapAction = SonosSoap.avTransportSoapAction("GetCurrentTransportActions"),
            envelope = SonosSoap.avTransportEnvelope("GetCurrentTransportActions")
        ))
        return SonosPlayback(player.roomName, state, track, actions)
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
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("sonos-widget-ssdp").apply { setReferenceCounted(false) }
        val socket = DatagramSocket()
        val locations = linkedSetOf<String>()
        try {
            lock.acquire()
            socket.soTimeout = 1_500
            val query = """
                M-SEARCH * HTTP/1.1
                HOST: 239.255.255.250:1900
                MAN: "ssdp:discover"
                MX: 1
                ST: urn:schemas-upnp-org:device:ZonePlayer:1

            """.trimIndent().replace("\n", "\r\n")
            val bytes = query.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("239.255.255.250"), 1900))
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
            return locations.toList()
        } finally {
            socket.close()
            if (lock.isHeld) lock.release()
        }
    }
}
```

- [ ] **Step 5: Implement preferences wrapper**

`app/src/main/java/com/superduper/sonoswidget/storage/SonosPrefs.kt`:

```kotlin
package com.superduper.sonoswidget.storage

import android.content.Context

class SonosPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sonos-widget", Context.MODE_PRIVATE)

    var selectedRoom: String?
        get() = prefs.getString(KEY_SELECTED_ROOM, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED_ROOM, value).apply()
        }

    companion object {
        private const val KEY_SELECTED_ROOM = "selected_room"
    }
}
```

- [ ] **Step 6: Run repository tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.sonos.SonosRepositoryTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/sonos app/src/main/java/com/superduper/sonoswidget/storage app/src/test/kotlin/com/superduper/sonoswidget/sonos/SonosRepositoryTest.kt
git commit -m "Add Sonos repository and discovery"
```

## Task 5: Add Widget Layouts and Renderer

**Files:**
- Create: `app/src/main/res/xml/sonos_widget_info.xml`
- Create: `app/src/main/res/drawable/widget_background.xml`
- Create: `app/src/main/res/drawable/widget_button.xml`
- Create: `app/src/main/res/drawable/widget_button_disabled.xml`
- Create: `app/src/main/res/drawable/artwork_fallback.xml`
- Create: `app/src/main/res/layout/widget_sonos_wide.xml`
- Create: `app/src/main/res/layout/widget_sonos_compact.xml`
- Create: `app/src/main/java/com/superduper/sonoswidget/widget/WidgetState.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt`
- Create: `app/src/test/kotlin/com/superduper/sonoswidget/widget/WidgetStateTest.kt`

- [ ] **Step 1: Write failing widget state tests**

```kotlin
package com.superduper.sonoswidget.widget

import com.superduper.sonoswidget.sonos.PlaybackState
import com.superduper.sonoswidget.sonos.SonosPlayback
import com.superduper.sonoswidget.sonos.SonosTrack
import com.superduper.sonoswidget.sonos.SonosTransportActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test
    fun mapsPlaybackToDisplayState() {
        val playback = SonosPlayback(
            roomName = "Dining Room",
            state = PlaybackState.PLAYING,
            track = SonosTrack("Song", "Artist", "http://example.test/art.jpg"),
            actions = SonosTransportActions(canPlay = false, canPause = true, canNext = true, canPrevious = false)
        )

        val state = WidgetState.fromPlayback(playback)

        assertEquals("Dining Room", state.room)
        assertEquals("Song", state.title)
        assertEquals("Artist", state.artist)
        assertEquals("http://example.test/art.jpg", state.artworkUrl)
        assertTrue(state.isPlaying)
        assertFalse(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.widget.WidgetStateTest`

Expected: FAIL with unresolved `WidgetState`.

- [ ] **Step 3: Create widget display state**

`app/src/main/java/com/superduper/sonoswidget/widget/WidgetState.kt`:

```kotlin
package com.superduper.sonoswidget.widget

import com.superduper.sonoswidget.sonos.PlaybackState
import com.superduper.sonoswidget.sonos.SonosPlayback

data class WidgetState(
    val room: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val previousEnabled: Boolean,
    val nextEnabled: Boolean,
    val controlsEnabled: Boolean
) {
    companion object {
        fun chooseRoom() = WidgetState(
            room = "Sonos",
            title = "Choose room",
            artist = "Open app",
            artworkUrl = null,
            isPlaying = false,
            previousEnabled = false,
            nextEnabled = false,
            controlsEnabled = false
        )

        fun unavailable(room: String?, message: String) = WidgetState(
            room = room ?: "Sonos",
            title = message,
            artist = "Tap to retry",
            artworkUrl = null,
            isPlaying = false,
            previousEnabled = false,
            nextEnabled = false,
            controlsEnabled = false
        )

        fun fromPlayback(playback: SonosPlayback) = WidgetState(
            room = playback.roomName,
            title = playback.track.title ?: "Nothing playing",
            artist = playback.track.artist ?: "",
            artworkUrl = playback.track.artworkUrl,
            isPlaying = playback.state == PlaybackState.PLAYING,
            previousEnabled = playback.actions.canPrevious,
            nextEnabled = playback.actions.canNext,
            controlsEnabled = playback.actions.canPlay || playback.actions.canPause
        )
    }
}
```

- [ ] **Step 4: Create widget XML resources**

`app/src/main/res/xml/sonos_widget_info.xml`:

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_name"
    android:initialLayout="@layout/widget_sonos_wide"
    android:minResizeWidth="245dp"
    android:minResizeHeight="56dp"
    android:minWidth="245dp"
    android:minHeight="56dp"
    android:previewLayout="@layout/widget_sonos_wide"
    android:resizeMode="horizontal"
    android:targetCellWidth="4"
    android:targetCellHeight="1"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

`app/src/main/res/drawable/widget_background.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#111923" />
    <corners android:radius="22dp" />
</shape>
```

`app/src/main/res/drawable/widget_button.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="#F8FAFC" />
</shape>
```

`app/src/main/res/drawable/widget_button_disabled.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="#263340" />
</shape>
```

`app/src/main/res/drawable/artwork_fallback.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="#E6B557"
        android:centerColor="#38A3A5"
        android:endColor="#243349"
        android:angle="135" />
    <corners android:radius="8dp" />
</shape>
```

- [ ] **Step 5: Create wide and compact layouts**

Use text glyphs only for layout wiring in this task. Replace them with vector drawables in Task 8.

`app/src/main/res/layout/widget_sonos_wide.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="12dp">

    <ImageView
        android:id="@+id/artwork"
        android:layout_width="72dp"
        android:layout_height="72dp"
        android:background="@drawable/artwork_fallback"
        android:contentDescription="@null"
        android:scaleType="centerCrop" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="10dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/room"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#95A9B8"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#F8FAFC"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/artist"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#C0CCD6"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/previous"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:gravity="center"
        android:text="Prev"
        android:textColor="#F8FAFC"
        android:textSize="10sp" />

    <TextView
        android:id="@+id/play_pause"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="@drawable/widget_button"
        android:gravity="center"
        android:text="Play"
        android:textColor="#111923"
        android:textSize="11sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/next"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:gravity="center"
        android:text="Next"
        android:textColor="#F8FAFC"
        android:textSize="10sp" />
</LinearLayout>
```

`app/src/main/res/layout/widget_sonos_compact.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginEnd="8dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/room"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#95A9B8"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#F8FAFC"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/artist"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="#C0CCD6"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/previous"
        android:layout_width="34dp"
        android:layout_height="34dp"
        android:gravity="center"
        android:text="Prev"
        android:textColor="#F8FAFC"
        android:textSize="10sp" />

    <TextView
        android:id="@+id/play_pause"
        android:layout_width="46dp"
        android:layout_height="46dp"
        android:background="@drawable/widget_button"
        android:gravity="center"
        android:text="Play"
        android:textColor="#111923"
        android:textSize="11sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/next"
        android:layout_width="34dp"
        android:layout_height="34dp"
        android:gravity="center"
        android:text="Next"
        android:textColor="#F8FAFC"
        android:textSize="10sp" />
</LinearLayout>
```

- [ ] **Step 6: Implement RemoteViews renderer**

`app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt`:

```kotlin
package com.superduper.sonoswidget.widget

import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import com.superduper.sonoswidget.R

object WidgetRenderer {
    fun render(
        context: Context,
        compact: Boolean,
        state: WidgetState,
        previousIntent: PendingIntent,
        playPauseIntent: PendingIntent,
        nextIntent: PendingIntent,
        rootIntent: PendingIntent
    ): RemoteViews {
        val layout = if (compact) R.layout.widget_sonos_compact else R.layout.widget_sonos_wide
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.room, state.room)
            setTextViewText(R.id.title, state.title)
            setTextViewText(R.id.artist, state.artist)
            setTextViewText(R.id.play_pause, if (state.isPlaying) "Pause" else "Play")
            setTextColor(R.id.previous, if (state.previousEnabled) 0xFFF8FAFC.toInt() else 0x6695A9B8)
            setTextColor(R.id.next, if (state.nextEnabled) 0xFFF8FAFC.toInt() else 0x6695A9B8)
            setOnClickPendingIntent(R.id.widget_root, rootIntent)
            setOnClickPendingIntent(R.id.previous, previousIntent)
            setOnClickPendingIntent(R.id.play_pause, playPauseIntent)
            setOnClickPendingIntent(R.id.next, nextIntent)
        }
    }
}
```

- [ ] **Step 7: Run widget state tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests com.superduper.sonoswidget.widget.WidgetStateTest`

Expected: PASS.

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res app/src/main/java/com/superduper/sonoswidget/widget app/src/test/kotlin/com/superduper/sonoswidget/widget
git commit -m "Add Sonos widget layouts"
```

## Task 6: Wire Widget Provider, Actions, and Refresh Worker

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/WidgetActionReceiver.kt`
- Create: `app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt`

- [ ] **Step 1: Modify the manifest**

Add these entries inside `<application>`:

```xml
<receiver
    android:name=".SonosWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/sonos_widget_info" />
</receiver>

<receiver
    android:name=".WidgetActionReceiver"
    android:exported="false" />
```

- [ ] **Step 2: Create widget provider**

`app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt`:

```kotlin
package com.superduper.sonoswidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class SonosWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetRefreshWorker.enqueue(context)
    }
}
```

- [ ] **Step 3: Create action receiver**

`app/src/main/java/com/superduper/sonoswidget/WidgetActionReceiver.kt`:

```kotlin
package com.superduper.sonoswidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.storage.SonosPrefs

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = SonosPrefs(context)
        val repository = SonosRepository(SonosDiscovery(context))
        runCatching {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> repository.togglePlayPause(prefs.selectedRoom)
                ACTION_NEXT -> repository.next(prefs.selectedRoom)
                ACTION_PREVIOUS -> repository.previous(prefs.selectedRoom)
            }
        }
        WidgetRefreshWorker.enqueue(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.superduper.sonoswidget.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.superduper.sonoswidget.action.NEXT"
        const val ACTION_PREVIOUS = "com.superduper.sonoswidget.action.PREVIOUS"
    }
}
```

- [ ] **Step 4: Create refresh worker**

`app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt`:

```kotlin
package com.superduper.sonoswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.sonos.SonosResult
import com.superduper.sonoswidget.storage.SonosPrefs
import com.superduper.sonoswidget.widget.WidgetRenderer
import com.superduper.sonoswidget.widget.WidgetState

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = SonosPrefs(context)
        val repository = SonosRepository(SonosDiscovery(context))
        val selectedRoom = prefs.selectedRoom
        val state = when (val playback = repository.currentPlayback(selectedRoom)) {
            is SonosResult.Available -> WidgetState.fromPlayback(playback.playback)
            is SonosResult.Unavailable -> if (selectedRoom == null) {
                WidgetState.chooseRoom()
            } else {
                WidgetState.unavailable(selectedRoom, playback.message)
            }
        }
        updateWidgets(context, state)
        return Result.success()
    }

    private fun updateWidgets(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SonosWidgetProvider::class.java))
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val compact = minWidth in 1..244
            val views = WidgetRenderer.render(
                context = context,
                compact = compact,
                state = state,
                previousIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, 1),
                playPauseIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, 2),
                nextIntent = broadcastIntent(context, WidgetActionReceiver.ACTION_NEXT, 3),
                rootIntent = activityIntent(context)
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val WORK_NAME = "sonos-widget-refresh"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }
    }
}
```

- [ ] **Step 5: Run build**

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt app/src/main/java/com/superduper/sonoswidget/WidgetActionReceiver.kt app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt
git commit -m "Wire widget refresh and controls"
```

## Task 7: Build Room Settings Screen

**Files:**
- Modify: `app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`

- [ ] **Step 1: Replace placeholder activity with room picker UI**

`app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`:

```kotlin
package com.superduper.sonoswidget

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.superduper.sonoswidget.sonos.SonosDiscovery
import com.superduper.sonoswidget.sonos.SonosRepository
import com.superduper.sonoswidget.storage.SonosPrefs
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: SonosPrefs
    private lateinit var status: TextView
    private lateinit var roomList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SonosPrefs(this)
        status = TextView(this).apply {
            textSize = 18f
            text = "Selected room: ${prefs.selectedRoom ?: "none"}"
        }
        roomList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val refresh = Button(this).apply {
            text = "Find Sonos rooms"
            setOnClickListener { loadRooms() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(refresh, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(roomList, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        loadRooms()
    }

    private fun loadRooms() {
        status.text = "Finding Sonos rooms..."
        roomList.removeAllViews()
        thread {
            val rooms = runCatching { SonosRepository(SonosDiscovery(this)).rooms() }.getOrDefault(emptyList())
            runOnUiThread {
                if (rooms.isEmpty()) {
                    status.text = "No Sonos rooms found"
                    return@runOnUiThread
                }
                status.text = "Selected room: ${prefs.selectedRoom ?: "none"}"
                rooms.forEach { room ->
                    roomList.addView(Button(this).apply {
                        text = room
                        setOnClickListener {
                            prefs.selectedRoom = room
                            status.text = "Selected room: $room"
                            WidgetRefreshWorker.enqueue(this@MainActivity)
                        }
                    })
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run build**

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/MainActivity.kt
git commit -m "Add Sonos room picker"
```

## Task 8: Replace Text Controls With Vector Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_previous.xml`
- Create: `app/src/main/res/drawable/ic_play.xml`
- Create: `app/src/main/res/drawable/ic_pause.xml`
- Create: `app/src/main/res/drawable/ic_next.xml`
- Modify: `app/src/main/res/layout/widget_sonos_wide.xml`
- Modify: `app/src/main/res/layout/widget_sonos_compact.xml`
- Modify: `app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt`

- [ ] **Step 1: Add vector icons**

`app/src/main/res/drawable/ic_previous.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#F8FAFC" android:pathData="M6,5h2v14H6zM19,6.5L10,12l9,5.5z" />
</vector>
```

`app/src/main/res/drawable/ic_play.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#111923" android:pathData="M8,5.5v13L18,12z" />
</vector>
```

`app/src/main/res/drawable/ic_pause.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#111923" android:pathData="M7,5h4v14H7zM13,5h4v14h-4z" />
</vector>
```

`app/src/main/res/drawable/ic_next.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#F8FAFC" android:pathData="M16,5h2v14h-2zM5,6.5L14,12l-9,5.5z" />
</vector>
```

- [ ] **Step 2: Replace control `TextView`s with `ImageView`s**

In both widget layout files, replace `@+id/previous`, `@+id/play_pause`, and `@+id/next` `TextView` elements with `ImageView` elements using the same ids. Use these attributes:

```xml
android:contentDescription="@null"
android:padding="8dp"
android:scaleType="center"
```

Set `android:src="@drawable/ic_previous"` for previous, `android:src="@drawable/ic_play"` for play/pause, and `android:src="@drawable/ic_next"` for next.

- [ ] **Step 3: Update renderer to switch icons**

Replace the `setTextViewText` call for `R.id.play_pause` and color calls for previous/next with:

```kotlin
setImageViewResource(R.id.play_pause, if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
setInt(R.id.previous, "setAlpha", if (state.previousEnabled) 255 else 90)
setInt(R.id.next, "setAlpha", if (state.nextEnabled) 255 else 90)
```

- [ ] **Step 4: Run build**

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable app/src/main/res/layout app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt
git commit -m "Use icon controls in widget"
```

## Task 9: Add Artwork Fetching

**Files:**
- Modify: `app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt`
- Modify: `app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt`

- [ ] **Step 1: Extend HTTP client for bytes**

Add to `SonosHttpClient`:

```kotlin
fun getBytes(url: String): ByteArray
```

Add to `JavaNetSonosHttpClient`:

```kotlin
override fun getBytes(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = connectTimeoutMs
    connection.readTimeout = readTimeoutMs
    connection.requestMethod = "GET"
    return try {
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}
```

- [ ] **Step 2: Add bitmap loading to renderer**

Add imports:

```kotlin
import android.graphics.BitmapFactory
import com.superduper.sonoswidget.sonos.JavaNetSonosHttpClient
```

Inside `render`, after setting text:

```kotlin
if (!compact && state.artworkUrl != null) {
    runCatching {
        val bytes = JavaNetSonosHttpClient(connectTimeoutMs = 1_500, readTimeoutMs = 2_500).getBytes(state.artworkUrl)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()?.let { bitmap ->
        setImageViewBitmap(R.id.artwork, bitmap)
    }
}
```

- [ ] **Step 3: Run build**

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/sonos/SonosHttpClient.kt app/src/main/java/com/superduper/sonoswidget/widget/WidgetRenderer.kt
git commit -m "Show Sonos artwork in widget"
```

## Task 10: Add Periodic Refresh Scheduling

**Files:**
- Modify: `app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt`
- Modify: `app/src/main/java/com/superduper/sonoswidget/MainActivity.kt`

- [ ] **Step 1: Add periodic scheduling helper**

In `WidgetRefreshWorker.Companion`, add:

```kotlin
fun schedulePeriodic(context: Context) {
    val request = androidx.work.PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
        30,
        java.util.concurrent.TimeUnit.MINUTES
    ).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "sonos-widget-periodic-refresh",
        androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
```

- [ ] **Step 2: Schedule periodic work from provider and activity**

In `SonosWidgetProvider.onUpdate`, before `WidgetRefreshWorker.enqueue(context)`, add:

```kotlin
WidgetRefreshWorker.schedulePeriodic(context)
```

In `MainActivity.onCreate`, after `prefs = SonosPrefs(this)`, add:

```kotlin
WidgetRefreshWorker.schedulePeriodic(this)
```

- [ ] **Step 3: Run build**

Run: `./gradlew :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/superduper/sonoswidget/WidgetRefreshWorker.kt app/src/main/java/com/superduper/sonoswidget/SonosWidgetProvider.kt app/src/main/java/com/superduper/sonoswidget/MainActivity.kt
git commit -m "Schedule widget refreshes"
```

## Task 11: Verify on Ben's Pixel

**Files:**
- Modify only if verification reveals a defect.

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Build debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Confirm ADB sees the phone**

Run: `adb devices`

Expected: One Pixel device is listed as `device`.

- [ ] **Step 4: Install the app**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 5: Launch settings**

Run: `adb shell monkey -p com.superduper.sonoswidget 1`

Expected: The app opens on the Pixel.

- [ ] **Step 6: Manual checks**

On the Pixel:

- Tap `Find Sonos rooms`.
- Choose `Dining Room`.
- Add the `Sonos Controller` widget to Pixel Launcher at 4x1.
- Confirm room, title, artist, and artwork display.
- Tap play/pause and confirm Sonos responds.
- Tap next and previous on Spotify.
- Start a radio or streaming source with unsupported skip behavior and confirm unavailable controls dim after refresh.
- Resize smaller and confirm artwork hides first.

- [ ] **Step 7: Fix any verification defects**

For each defect, write the smallest failing unit test possible first, run it to see it fail, implement the fix, run the focused test, then run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

- [ ] **Step 8: Commit verification fixes or final verification note**

If code changed:

```bash
git add app
git commit -m "Fix Pixel widget verification issues"
```

If no code changed:

```bash
git status --short
```

Expected: no uncommitted source changes.

## Self-Review

Spec coverage:

- Native Kotlin app: Tasks 1 and 7.
- Sideloadable debug APK: Tasks 1 and 11.
- 4x1 widget: Tasks 5, 6, 8, and 11.
- Responsive compact layout: Tasks 5 and 11.
- Play/pause/previous/next: Tasks 3, 4, 6, and 11.
- Track metadata and artwork: Tasks 2, 5, 9, and 11.
- Room selection: Tasks 4 and 7.
- Local Sonos UPnP/SOAP: Tasks 2, 3, and 4.
- Quiet error states: Tasks 5 and 6.
- Unit and integration-style tests: Tasks 1, 2, 3, 4, 5, and 11.

Placeholder scan:

- No placeholder markers or unresolved implementation notes remain.

Type consistency:

- `SonosRepository`, `SonosGateway`, `SonosResult`, `WidgetState`, and `WidgetRenderer` signatures are introduced before later tasks call them.

## References

- Android widget sizing: https://developer.android.com/design/ui/mobile/guides/widgets/sizing
- Android flexible widget layouts: https://developer.android.com/develop/ui/views/appwidgets/layouts
- Android local network permission: https://developer.android.com/privacy-and-security/local-network-permission
- Android Gradle plugin release notes: https://developer.android.com/studio/releases/gradle-plugin
- WorkManager release notes: https://developer.android.com/jetpack/androidx/releases/work
- Sonos AVTransport planning reference: https://sonos.svrooij.io/services/av-transport
