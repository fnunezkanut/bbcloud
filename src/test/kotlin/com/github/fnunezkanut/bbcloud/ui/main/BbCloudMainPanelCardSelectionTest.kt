package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ProjectRepositoryStatus
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudMainPanelCardSelectionTest {
    @Test
    fun `viewCardFor - shows the listing only for a verified Bitbucket repository`() {
        // GIVEN
        val status = ProjectRepositoryStatus.Found(
            repositoryWebUrl = "https://bitbucket.org/example-workspace/example-repo",
            avatarUrl = null
        )

        // WHEN
        val card = viewCardFor(status)

        // THEN
        card shouldBe ViewCard.Listing
    }

    @Test
    fun `viewCardFor - shows the centered project status for every non-Bitbucket outcome`() {
        // GIVEN
        val notFound = ProjectRepositoryStatus.NotFound(
            repositoryWebUrl = "https://bitbucket.org/example-workspace/example-repo"
        )
        val noLocalProjectPath = ProjectRepositoryStatus.NoLocalProjectPath
        val checkFailed = ProjectRepositoryStatus.CheckFailed(
            repositoryWebUrl = "https://bitbucket.org/example-workspace/example-repo"
        )
        val checkFailedWithoutUrl = ProjectRepositoryStatus.CheckFailed(repositoryWebUrl = null)

        // WHEN
        val notFoundCard = viewCardFor(notFound)
        val noLocalProjectPathCard = viewCardFor(noLocalProjectPath)
        val checkFailedCard = viewCardFor(checkFailed)
        val checkFailedWithoutUrlCard = viewCardFor(checkFailedWithoutUrl)

        // THEN
        assertSoftly {
            notFoundCard shouldBe ViewCard.ProjectStatus
            noLocalProjectPathCard shouldBe ViewCard.ProjectStatus
            checkFailedCard shouldBe ViewCard.ProjectStatus
            checkFailedWithoutUrlCard shouldBe ViewCard.ProjectStatus
        }
    }
}