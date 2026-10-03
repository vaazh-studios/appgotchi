package com.vaazhstudios.appgotchi.data

private val appleKeyFileName = Regex("""^AuthKey_([A-Z0-9]+)\.p8$""")

/** Apple names downloaded keys `AuthKey_<KEYID>.p8`; returns null once the file has been renamed. */
fun keyIdFromFileName(fileName: String): String? = appleKeyFileName.find(fileName)?.groupValues?.get(1)

/** Key files are small text files: drop a UTF-8 byte-order mark and surrounding whitespace. */
fun cleanKeyFileText(text: String): String = text.removePrefix("\uFEFF").trim()

fun hasExtension(fileName: String, extension: String): Boolean =
    fileName.length > extension.length + 1 && fileName.endsWith(".$extension", ignoreCase = true)
