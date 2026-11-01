package com.github.fnunezkanut.bbcloud.auth

import java.util.Base64

internal object BbCloudConsumerMaterial {
    private const val MASK_SEED = "bbcloud.consumer.v1"
    private const val PART_A = "JS05XR8GJm8lGiMHAlVQKhgfZQUlOgQELwxLKCckCzI="
    private const val PART_B =
        "IzYsLSoBXG0zLVsmRD4KGXFOBA47CDM6Eg9pIi0jCQw9MB5EEEQgV1ccIyIHbxYwJjYeIDQIGCBZEBhaDV00MEsOAgo1QC5SN21OAQ=="
    private const val PART_C = "AQ4KCQEBO0cH"

    private val primaryValue: String by lazy { decode(PART_A) }
    private val secondaryValue: String by lazy { decode(PART_B) }
    private val primaryFieldValue: String by lazy { decode(PART_C) }

    fun primary(): String {
        return primaryValue
    }

    fun secondary(): String {
        return secondaryValue
    }

    fun primaryField(): String {
        return primaryFieldValue
    }

    internal fun decode(encoded: String): String {
        val mask = MASK_SEED.encodeToByteArray()
        val raw = Base64.getDecoder().decode(encoded)
        val bytes = ByteArray(raw.size) { index ->
            (raw[index].toInt() xor mask[index % mask.size].toInt()).toByte()
        }
        return bytes.decodeToString()
    }

    internal fun encode(plain: String): String {
        val mask = MASK_SEED.encodeToByteArray()
        val raw = plain.encodeToByteArray()
        val bytes = ByteArray(raw.size) { index ->
            (raw[index].toInt() xor mask[index % mask.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }
}