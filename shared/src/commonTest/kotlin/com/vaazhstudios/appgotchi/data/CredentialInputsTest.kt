package com.vaazhstudios.appgotchi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialInputsTest {
    @Test
    fun privateKeysAreRecognised() {
        assertTrue(looksLikePrivateKey("-----BEGIN PRIVATE KEY-----\nabc"))
        assertTrue(looksLikePrivateKey("""{"private_key":"-----BEGIN PRIVATE KEY-----"}"""))
        assertFalse(looksLikePrivateKey("1234567890123456789"))
    }

    @Test
    fun aBareKeyBodyIsRecognisedWithoutItsHeaderLines() {
        assertTrue(looksLikePrivateKey("MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg"))
    }

    @Test
    fun realIdsAreNotMistakenForKeys() {
        assertFalse(looksLikePrivateKey("1a2b3c4d-1a2b-1a2b-1a2b-1a2b3c4d5e6f"))
        assertFalse(looksLikePrivateKey("12345678901234567891"))
        assertFalse(looksLikePrivateKey("ABCDE12345"))
    }

    @Test
    fun usersAndPermissionsLinkIsBuiltFromAValidAccountId() {
        assertEquals(
            "https://play.google.com/console/u/0/developers/1234567890123456789/users-and-permissions",
            usersAndPermissionsUrl("1234567890123456789"),
        )
        assertEquals(
            "https://play.google.com/console/u/0/developers/1234567890123456789/users-and-permissions",
            usersAndPermissionsUrl("  1234567890123456789 "),
        )
    }

    @Test
    fun usersAndPermissionsLinkIsNullForAnInvalidAccountId() {
        assertNull(usersAndPermissionsUrl("abc"))
        assertNull(usersAndPermissionsUrl(""))
        assertNull(usersAndPermissionsUrl("123"))
    }

    @Test
    fun emptyInputsHaveNoProblem() {
        assertNull(developerAccountIdProblem(""))
        assertNull(issuerIdProblem("   "))
    }

    @Test
    fun developerAccountIdMustBeTheLongNumber() {
        assertNull(developerAccountIdProblem("1234567890123456789"))
        assertNull(developerAccountIdProblem(" 1234567890123456789 "))
        assertEquals(
            "That isn’t a developer account ID. It’s the long number shown as Account ID on Play Console’s home page.",
            developerAccountIdProblem("nirmal jeffrey"),
        )
        assertEquals(
            "That isn’t a developer account ID. It’s the long number shown as Account ID on Play Console’s home page.",
            developerAccountIdProblem("12345"),
        )
    }

    @Test
    fun issuerIdMustBeAUuid() {
        assertNull(issuerIdProblem("57246542-96fe-1a63-e053-0824d011072a"))
        assertNull(issuerIdProblem("C593851A-EAE0-482A-ABFF-54DB03D3D937"))
        assertEquals(
            "An Issuer ID looks like 1a2b3c4d-1a2b-1a2b-1a2b-1a2b3c4d5e6f.",
            issuerIdProblem("7XK2M9Q4TB"),
        )
    }
}
