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

  @Test
  fun testResolveSafeMimeType_avoidsWildcards() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.transfer.FileTransferEngine(context)
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("archive.unknownext", "*/*"))
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("photo.unknownext", "image/*"))
    assertEquals("application/octet-stream", engine.resolveSafeMimeType("noext", ""))
    assertEquals("application/pdf", engine.resolveSafeMimeType("doc.pdf", "application/pdf"))
  }

  @Test
  @Config(sdk = [28])
  fun testPermissions_android9() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
  }

  @Test
  @Config(sdk = [29, 30])
  fun testPermissions_android10And11() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    assertEquals(2, perms.size)
  }

  @Test
  @Config(sdk = [31, 32])
  fun testPermissions_android12() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(perms.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
  }

  @Test
  @Config(sdk = [33, 34, 36])
  fun testPermissions_android13To17() {
    val perms = getRequiredPermissions().toSet()
    assertTrue(perms.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
    assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
    assertEquals(4, perms.size)
  }

  @Test
  @Config(sdk = [28, 34])
  fun testFallbackDestination_usesMiniShareFolder() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.transfer.FileTransferEngine(context)
    val dest = engine.getFallbackFile("../../sample.txt")
    assertEquals("Mini Share", dest.parentFile?.name)
    assertEquals("sample.txt", dest.name)
    assertEquals("notes", engine.sanitizeFileName("/evil/path/notes"))
  }
}
