// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Expected bytes were produced by the official protobuf runtime (Python, protobuf 7.x) from polo.proto and
 * remotemessage.proto, so these tests compare our hand-written encoder with the reference serializer.
 */
class AtvMessagesTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun pairingMessagesMatchReferenceSerializer() {
        assertEquals("080210c80152160a0961747672656d6f74651209594169506c61796572", PoloMessages.pairingRequest("YAiPlayer").hex())
        assertEquals("080210c801a201080a04080310061801", PoloMessages.options().hex())
        assertEquals("080210c801f201080a04080310061001", PoloMessages.configuration().hex())
        assertEquals(
            "080210c801c202220a20000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            PoloMessages.secret(ByteArray(32) { it.toByte() }).hex(),
        )
    }

    @Test fun pairingRepliesAreRecognised() {
        assertEquals(PoloMessages.Kind.PAIRING_REQUEST_ACK, PoloMessages.parse(hex("080210c8015a040a025456")).kind)
        assertEquals(PoloMessages.Kind.CONFIGURATION_ACK, PoloMessages.parse(hex("080210c801fa0100")).kind)
        assertEquals(PoloMessages.Kind.SECRET_ACK, PoloMessages.parse(hex("080210c801ca02030a0101")).kind)
        assertEquals(PoloMessages.Kind.OPTIONS, PoloMessages.parse(PoloMessages.options()).kind)
        val bad = PoloMessages.parse(hex("0802109203"))
        assertEquals(402, bad.status)
        assertEquals(PoloMessages.Kind.OTHER, bad.kind)
    }

    @Test fun commandMessagesMatchReferenceSerializer() {
        val all = AtvFeatures.REQUESTED
        assertEquals("0a1c08e704121718012201312a0961747672656d6f74653205312e302e30", RemoteMessages.configureReply(all).hex())
        assertEquals("0a1c08e604121718012201312a0961747672656d6f74653205312e302e30", RemoteMessages.configureReply(614).hex())
        assertEquals("120308e704", RemoteMessages.setActive(all).hex())
        assertEquals("4a020811", RemoteMessages.pingResponse(17).hex())
        assertEquals("520408171003", RemoteMessages.keyInject(AtvKeys.DPAD_CENTER, AtvDirection.SHORT).hex())
        assertEquals("5204081a1001", RemoteMessages.keyInject(AtvKeys.POWER, AtvDirection.START_LONG).hex())
        assertEquals("aa011c080310051a1608011212080610061a0cd09fd180d0b8d0b2d0b5d182", RemoteMessages.imeText("Привет", 3, 5).hex())
        assertEquals(
            "d205220a206d61726b65743a2f2f6c61756e63683f69643d6f72672e78626d632e6b6f6469",
            RemoteMessages.appLink(AtvAppLinks.normalize("org.xbmc.kodi")).hex(),
        )
        assertEquals(
            "d205190a1768747470733a2f2f7777772e796f75747562652e636f6d",
            RemoteMessages.appLink(AtvAppLinks.normalize("https://www.youtube.com")).hex(),
        )
    }

    @Test fun incomingCommandMessagesAreParsed() {
        assertEquals(
            RemoteMessages.Configure(622, "SHIELD", "NVIDIA", "5.2"),
            RemoteMessages.parse(hex("0a1a08ee0412150a06534849454c4412064e56494449413203352e32")),
        )
        assertEquals(RemoteMessages.Ping(42), RemoteMessages.parse(hex("4204082a1007")))
        assertEquals(RemoteMessages.Ping(-1), RemoteMessages.parse(hex("420b08ffffffffffffffffff01")))
        assertEquals(RemoteMessages.Started(true), RemoteMessages.parse(hex("c202020801")))
        assertEquals(RemoteMessages.Volume(35, 100, true), RemoteMessages.parse(hex("9203091a0158306438234001")))
        assertEquals(
            RemoteMessages.ImeCounters(9, 4, "com.google.android.youtube.tv"),
            RemoteMessages.parse(hex("a201270a210809621d636f6d2e676f6f676c652e616e64726f69642e796f75747562652e747612020804")),
        )
        assertEquals(RemoteMessages.ImeCounters(null, 6, null), RemoteMessages.parse(hex("b2010412020806")))
        assertEquals(RemoteMessages.SetActive, RemoteMessages.parse(hex("1200")))
        // A negative ping value must be echoed back unchanged.
        assertEquals(
            RemoteMessages.pingResponse(-1).hex(),
            "4a0b08ffffffffffffffffff01",
        )
    }

    @Test fun brokenBytesAreRejected() {
        for (bad in listOf("0a", "0a05010203", "42ff", "07")) {
            try {
                RemoteMessages.parse(hex(bad))
                fail("accepted $bad")
            } catch (_: AtvProtoException) {
            }
        }
    }

    @Test fun framingRoundTripsLongAndShortMessages() {
        val out = ByteArrayOutputStream()
        val short = byteArrayOf(1, 2, 3)
        val long = ByteArray(300) { it.toByte() }
        AtvFraming.write(out, short)
        AtvFraming.write(out, long)
        val bytes = out.toByteArray()
        assertEquals(3, bytes[0].toInt())
        assertEquals(0xAC, bytes[4].toInt() and 0xFF) // 300 = 0xAC 0x02
        assertEquals(0x02, bytes[5].toInt())
        val input = ByteArrayInputStream(bytes)
        assertArrayEquals(short, AtvFraming.read(input))
        assertArrayEquals(long, AtvFraming.read(input))
    }

    @Test fun pairingSecretMatchesReferenceVectors() {
        val cm = BigInteger("C" + "3".repeat(254) + "B", 16)
        val sm = BigInteger("A1" + "5".repeat(252) + "7", 16)
        val e = BigInteger.valueOf(65537)
        val one = AtvPairingCode.secret(cm, e, sm, e, "CD1234")!!
        assertEquals("cdcc204fddfa5b5bff5e8d5f6e905ef5dc34fb493c36dae6e4937159ff250cfa", one.hex())
        val two = AtvPairingCode.secret(cm, e, sm, e, "b9-be ef")!!
        assertEquals("b9534973bf6a59c1f0316ac174a1b472d3307749a23b97b093cfb5e33dde7246", two.hex())
        // Wrong check byte: rejected locally.
        assertNull(AtvPairingCode.secret(cm, e, sm, e, "CE1234"))
        assertNull(AtvPairingCode.normalize("12345"))
        assertNull(AtvPairingCode.normalize("12345G"))
        assertEquals("ABCDEF", AtvPairingCode.normalize("ab cd-ef"))
    }

    @Test fun unsignedDropsSignByteOnly() {
        assertArrayEquals(byteArrayOf(0x80.toByte()), AtvPairingCode.unsigned(BigInteger.valueOf(128)))
        assertArrayEquals(byteArrayOf(1, 0, 1), AtvPairingCode.unsigned(BigInteger.valueOf(65537)))
    }

    @Test fun onlyHomeNetworkAddressesAreAllowed() {
        for (ok in listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "127.0.0.1", "169.254.1.1", "fd12::1", "fe80::1")) {
            assertTrue(ok, AtvTls.isLocalAddress(InetAddress.getByName(ok)))
        }
        for (bad in listOf("8.8.8.8", "100.64.1.1", "2001:4860::8888")) {
            assertFalse(bad, AtvTls.isLocalAddress(InetAddress.getByName(bad)))
        }
    }

    @Test fun appLinksAcceptLinksAndPackagesOnly() {
        assertEquals("netflix://", AtvAppLinks.normalize("netflix://"))
        assertEquals("market://launch?id=com.google.android.youtube.tv", AtvAppLinks.normalize(" com.google.android.youtube.tv "))
        for (bad in listOf("", "youtube", "rm -rf /")) {
            try {
                AtvAppLinks.normalize(bad)
                fail("accepted '$bad'")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun digitKeys() {
        assertEquals(7, AtvKeys.digit(0))
        assertEquals(16, AtvKeys.digit(9))
    }
}
