package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FileItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.model.formatBytes
import com.example.model.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mini Share", appName)
  }

  @Test
  fun testFormatBytes() {
    assertEquals("0 B", formatBytes(0))
    assertEquals("500 B", formatBytes(500))
    assertEquals("1.0 KB", formatBytes(1024))
    assertEquals("10.0 MB", formatBytes(10 * 1024 * 1024))
    assertEquals("1.5 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
  }

  @Test
  fun testFormatSeconds() {
    assertEquals("00:00", formatSeconds(0))
    assertEquals("00:45", formatSeconds(45))
    assertEquals("02:15", formatSeconds(135))
    assertEquals("01:05:00", formatSeconds(3900))
  }

  @Test
  fun testFileItemProgress() {
    val file = FileItem(
      id = "1",
      name = "test.mp4",
      sizeBytes = 1000L,
      transferredBytes = 500L,
      status = TransferStatus.TRANSFERRING
    )
    assertEquals(0.5f, file.progress, 0.001f)
    assertEquals("500 B", file.formattedTransferred)
  }
}
