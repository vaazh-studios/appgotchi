package com.vaazhstudios.appgotchi.data

private val appleKeyFileName = Regex("""^AuthKey_([A-Z0-9]+)\.p8$""")

/** Apple names downloaded keys `AuthKey_<KEYID>.p8`; returns null once the file has been renamed. */
fun keyIdFromFileName(fileName: String): String? = appleKeyFileName.find(fileName)?.groupValues?.get(1)

/** Key files are small text files: drop a UTF-8 byte-order mark and surrounding whitespace. */
fun cleanKeyFileText(text: String): String = text.removePrefix("\uFEFF").trim()

/** Real key files are well under 1 KiB; anything near this size is the wrong file and isn't worth reading. */
const val MAX_KEY_FILE_BYTES = 64L * 1024

fun hasExtension(fileName: String, extension: String): Boolean =
    fileName.length > extension.length + 1 && fileName.endsWith(".$extension", ignoreCase = true)
