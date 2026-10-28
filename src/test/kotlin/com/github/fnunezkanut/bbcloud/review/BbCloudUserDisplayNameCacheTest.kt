package com.github.fnunezkanut.bbcloud.review

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudUserDisplayNameCacheTest {
    @Nested
    inner class LookupTests {
        @Test
        fun `cachedName - should report a miss for an account that was never looked up`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()

            // WHEN
            val result = cache.cachedName(uuidKey = "11111111-1111-1111-1111-111111111111")

            // THEN
            result.shouldBeNull()
        }

        @Test
        fun `cachedName - should return the resolved name after put`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()
            cache.put(uuidKey = "11111111-1111-1111-1111-111111111111", displayName = "Anna")

            // WHEN
            val result = cache.cachedName(uuidKey = "11111111-1111-1111-1111-111111111111")

            // THEN
            result shouldBe CachedUserName(displayName = "Anna")
        }

        @Test
        fun `cachedName - should return a negative entry rather than a miss after a failed lookup`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()
            cache.put(uuidKey = "22222222-2222-2222-2222-222222222222", displayName = null)

            // WHEN
            val result = cache.cachedName(uuidKey = "22222222-2222-2222-2222-222222222222")

            // THEN
            result shouldBe CachedUserName(displayName = null)
        }

        @Test
        fun `put - should store a blank name as a negative result`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()

            // WHEN
            cache.put(uuidKey = "22222222-2222-2222-2222-222222222222", displayName = "   ")

            // THEN
            cache.cachedName(uuidKey = "22222222-2222-2222-2222-222222222222") shouldBe CachedUserName(displayName = null)
        }

        @Test
        fun `put - should overwrite an earlier negative result with a resolved name`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()
            cache.put(uuidKey = "22222222-2222-2222-2222-222222222222", displayName = null)

            // WHEN
            cache.put(uuidKey = "22222222-2222-2222-2222-222222222222", displayName = "Ben")

            // THEN
            cache.cachedName(uuidKey = "22222222-2222-2222-2222-222222222222") shouldBe CachedUserName(displayName = "Ben")
        }
    }

    @Nested
    inner class SeedingTests {
        @Test
        fun `putAll - should seed every known name so later lookups skip them`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()
            val known = mapOf(
                "11111111-1111-1111-1111-111111111111" to "Anna",
                "22222222-2222-2222-2222-222222222222" to "Ben"
            )

            // WHEN
            cache.putAll(names = known)

            // THEN
            cache.cachedName(uuidKey = "11111111-1111-1111-1111-111111111111") shouldBe CachedUserName(displayName = "Anna")
            cache.cachedName(uuidKey = "22222222-2222-2222-2222-222222222222") shouldBe CachedUserName(displayName = "Ben")
        }

        @Test
        fun `cachedName - should treat keys as already normalized and not match a braced form`() {
            // GIVEN
            val cache = BbCloudUserDisplayNameCache()
            cache.put(uuidKey = "11111111-1111-1111-1111-111111111111", displayName = "Anna")

            // WHEN
            val result = cache.cachedName(uuidKey = "{11111111-1111-1111-1111-111111111111}")

            // THEN
            result.shouldBeNull()
        }
    }
}