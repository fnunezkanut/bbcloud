package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class AvatarUrlUpgraderTest {
    @Test
    fun `upgrade - should enlarge Bitbucket avatar path`() {
        // GIVEN
        val url = "https://bitbucket.org/account/user/avatar/32/?foo=bar"

        // WHEN
        val upgraded = AvatarUrlUpgrader.upgrade(url = url, scale = 2.0)

        // THEN
        upgraded shouldBe "https://bitbucket.org/account/user/avatar/96/?foo=bar"
    }

    @Test
    fun `upgrade - should set Gravatar size while preserving other parameters`() {
        // GIVEN
        val url = "https://secure.gravatar.com/avatar/abc?s=32&d=mm"

        // WHEN
        val upgraded = AvatarUrlUpgrader.upgrade(url = url, scale = 2.0)

        // THEN
        upgraded shouldBe "https://secure.gravatar.com/avatar/abc?d=mm&s=96"
    }

    @Test
    fun `upgrade - should leave unknown and Bytebucket URLs unchanged`() {
        // GIVEN
        val unknown = "https://avatar.example/avatar/32/"
        val repository = "https://bytebucket.org/ravatar/%7Babc%7D?ts=java"

        // WHEN
        val unknownResult = AvatarUrlUpgrader.upgrade(url = unknown, scale = 2.0)
        val repositoryResult = AvatarUrlUpgrader.upgrade(url = repository, scale = 2.0)

        // THEN
        assertSoftly {
            unknownResult shouldBe unknown
            repositoryResult shouldBe repository
        }
    }

    @Test
    fun `upgrade - should be null safe`() {
        AvatarUrlUpgrader.upgrade(url = null, scale = 2.0) shouldBe null
    }

    @Test
    fun `devicePixelSize - should clamp large source requests`() {
        AvatarUrlUpgrader.devicePixelSize(logicalSize = 1024, scale = 2.0) shouldBe 512
    }
}