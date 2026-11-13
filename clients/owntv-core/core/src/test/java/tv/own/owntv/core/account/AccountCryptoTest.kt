// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vectors were made by the SITE (yai-cloud site/src/keys.js, WebCrypto in Chrome/Node): what the cabinet encrypts,
 * the app must open, and the other way round.
 */
class AccountCryptoTest {
    private val password = "secret-pass-1"
    private val recovery = "ABCD-EFGH-JKLM-NPQR-STUV-WXYZ"
    private val dataKey = AccountCrypto.unb64u("zwed8v9A3Olk9dpniFxy0T2eXt-l0NWz0n0WAcJGIAU")
    private val keySalt = "fEySBGAdQRbcFybleHZ6Xw"
    private val wrappedByPassword = "nkif7fhTBEgZ5z751ElpCp1DZJpkrNU4K-fOrqCePbhRJqwDKWafUQUQ_ne6IF2g7YX-I-we1PkRyti2"
    private val wrappedByRecovery = "htiKKpZRcdoFd6ofLqbVkOitIuxmeNJ4amhCOYrz32CIPbZcqEBRwSfi9GPjjDL8MVv4oQJ8paWqhDnFcEH17w4Tc_YLmE67Sao88g"
    private val secret = "PIfktOgAIkal6L1PNd3iJoapaReieFXb0-Whzu96sY0VrRPEAi5odTRQ_SkXTb4_2w74nMeB2bgU5mopsvklqs2-F7kx-89d2w"
    private val devPriv = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQghqcgE51aYJU_XW1mc9cGJMwW-YRH5S1cI_OTnU3qV_ihRANCAATqubx-WcDZuwGZEao3TXzynqzGzx0X36pKrmGXGXQIpbLwYkVEb0X-JV9JzVgaVWcBjXuFoQbzELRL6O6vUx-B"
    private val devPub = "BOq5vH5ZwNm7AZkRqjdNfPKerMbPHRffqkquYZcZdAilsvBiRURvRf4lX0nNWBpVZwGNe4WhBvMQtEvo7q9TH4E"
    private val wrappedForDevice = "BBzkZUlAKL6QiYzZ1Ge-HCmEHhpcJ2VxLItoFjJOMZxfRRlPGsagJlVTHCCR1vIVvlHDj7ztusl8Wh5z_AQjQJimHG-zqDWUzY9nGOt5TMZvLfXxRH8snZ7RQBRHSaJUraet_M4MTtkjGxE9cfXxeknV1v9T0pFeMod6qgQ"

    @Test fun opensSiteWraps() {
        assertArrayEquals(dataKey, AccountCrypto.unlockWithPassword(keySalt, wrappedByPassword, password))
        assertArrayEquals(dataKey, AccountCrypto.unlockWithRecovery(wrappedByRecovery, recovery.lowercase().replace("-", " ")))
        assertTrue(runCatching { AccountCrypto.unlockWithPassword(keySalt, wrappedByPassword, "wrong") }.isFailure)
    }

    @Test fun opensSiteSecret() {
        assertEquals("{\"password\":\"p@ss\",\"mac\":\"00:1A:79:00:00:01\"}", AccountCrypto.decryptSecret(dataKey, secret))
    }

    @Test fun receivesKeyFromSiteAsTv() {
        val priv = AccountCrypto.privateFromPkcs8(AccountCrypto.unb64u(devPriv))
        assertArrayEquals(dataKey, AccountCrypto.unwrapOnDevice(priv, wrappedForDevice))
    }

    @Test fun roundTripsOwnFormats() {
        val k = AccountCrypto.newAccountKeys("pass-word-1", AccountCrypto.newRecoveryCode().also { code ->
            assertTrue(Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){5}$").matches(code))
        })
        assertArrayEquals(k.dataKey, AccountCrypto.unlockWithPassword(k.keySalt, k.wrappedByPassword, "pass-word-1"))
        val s = AccountCrypto.encryptSecret(k.dataKey, "{\"x\":1}")
        assertEquals("{\"x\":1}", AccountCrypto.decryptSecret(k.dataKey, s))
        val tv = AccountCrypto.newDeviceKeyPair()
        val w = AccountCrypto.wrapForDevice(k.dataKey, AccountCrypto.publicRaw(tv.public))
        assertArrayEquals(k.dataKey, AccountCrypto.unwrapOnDevice(tv.private, w))
        assertEquals(65, AccountCrypto.unb64u(AccountCrypto.publicRaw(tv.public)).size)
        // the site's device key, given raw, is accepted
        assertEquals(devPub, AccountCrypto.publicRaw(AccountCrypto.publicFromRaw(AccountCrypto.unb64u(devPub))))
    }
}
