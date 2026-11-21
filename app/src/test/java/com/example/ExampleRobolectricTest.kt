package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.CivicCategory
import com.example.domain.CpgramsKnowledgeBase
import com.example.domain.GeoClusterUtils
import com.example.domain.PublicCivicReport
import com.example.domain.PublicReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CivicLens", appName)
  }

  @Test
  fun `cpgrams knowledge base resolves official citations`() {
    val route = CpgramsKnowledgeBase.resolveRoute(CivicCategory.ROADS)
    assertEquals("PWD-RD-01", route.departmentCode)
    assertTrue(route.citationUrl.startsWith("https://"))
  }

  @Test
  fun `duplicate clustering matches nearby open report in same ward`() {
    val existing = PublicCivicReport(
      id = "pub_1",
      title = "Waterlogged pothole near bus stop",
      category = CivicCategory.ROADS.name,
      wardName = "Ward 12 - Indiranagar",
      geoCellLat = 12.97,
      geoCellLng = 77.64,
      reportCount = 3L,
      status = PublicReportStatus.OPEN.name
    )
    val matches = GeoClusterUtils.findPotentialDuplicates(
      category = CivicCategory.ROADS,
      wardName = "Ward 12",
      lat = 12.971,
      lng = 77.641,
      title = "Deep pothole near school bus stop",
      existingPublicReports = listOf(existing)
    )
    assertEquals(1, matches.size)
    assertEquals("pub_1", matches.first().id)
  }
}
