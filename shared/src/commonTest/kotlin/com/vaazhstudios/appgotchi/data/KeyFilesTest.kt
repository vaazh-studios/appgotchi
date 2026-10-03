package com.vaazhstudios.appgotchi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyFilesTest {
    @Test
    fun keyIdIsReadFromApplesFileName() {
        assertEquals("7XK2M9Q4TB", keyIdFromFileName("AuthKey_7XK2M9Q4TB.p8"))
    }

    @Test
    fun renamedFilesHaveNoKeyId() {
        assertNull(keyIdFromFileName("my-apple-key.p8"))
        assertNull(keyIdFromFileName("AuthKey_7XK2M9Q4TB.p8.txt"))
        assertNull(keyIdFromFileName("AuthKey_.p8"))
    }

    @Test
    fun cleaningStripsByteOrderMarkAndWhitespace() {
        assertEquals("-----BEGIN PRIVATE KEY-----", cleanKeyFileText("﻿  -----BEGIN PRIVATE KEY-----\n\n"))
    }

    @Test
    fun extensionCheckIgnoresCase() {
        assertTrue(hasExtension("AuthKey_ABC.P8", "p8"))
        assertTrue(hasExtension("key.json", "json"))
        assertFalse(hasExtension("key.json.txt", "json"))
        assertFalse(hasExtension("p8", "p8"))
    }
}
