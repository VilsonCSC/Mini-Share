package com.example.transfer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.FileItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

class FileTransferEngine(private val context: Context) {

    companion object {
        const val PORT = 8888
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
        private const val MAGIC_HEADER = 0x50325046 // 'P2PF'
        private const val CODE_DATA = 1
        private const val CODE_SKIP = 2
        private const val CODE_CANCELLED_MID = -1
        private const val CODE_EOF = 0
        private const val CONNECT_MAX_ATTEMPTS = 25
        private const val CONNECT_RETRY_DELAY_MS = 400L
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val isRunning = AtomicBoolean(false)
    private val cancelledFileIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun cancelFile(fileId: String) {
        cancelledFileIds.add(fileId)
    }

    fun isFileCancelled(fileId: String): Boolean {
        return cancelledFileIds.contains(fileId)
    }

    fun stopTransfer() {
        isRunning.set(false)
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
    }

    private suspend fun openPeerSocket(
        isGroupOwner: Boolean,
        groupOwnerAddress: String
    ): Socket {
        if (isGroupOwner) {
            val srv = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 30000
                bind(InetSocketAddress(PORT))
            }
            serverSocket = srv
            val accepted = srv.accept()
            try {
                srv.close()
            } catch (_: Exception) {}
            serverSocket = null
            accepted.tcpNoDelay = true
            accepted.soTimeout = 60000
            clientSocket = accepted
            return accepted
        } else {
            val peerAddress = resolveLocalPeerAddress(groupOwnerAddress)
            var lastException: Exception? = null
            repeat(CONNECT_MAX_ATTEMPTS) { attempt ->
                if (!isRunning.get() || !coroutineContext.isActive) {
                    throw CancellationException("Transfer cancelled")
                }
                val socket = Socket()
                try {
                    val localP2pAddress = findLocalP2pInterfaceAddress()
                    if (localP2pAddress != null) {
                        socket.bind(InetSocketAddress(localP2pAddress, 0))
                    } else {
                        socket.bind(null)
                    }
                    socket.connect(InetSocketAddress(peerAddress, PORT), 3000)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 60000
                    clientSocket = socket
                    return socket
                } catch (e: IOException) {
                    lastException = e
                    try {
                        socket.close()
                    } catch (_: Exception) {}
                    if (attempt < CONNECT_MAX_ATTEMPTS - 1) {
                        delay(CONNECT_RETRY_DELAY_MS)
                    }
                }
            }
            throw lastException ?: IOException("Unable to connect to peer")
        }
    }

    private fun findLocalP2pInterfaceAddress(): InetAddress? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackP2pSubnet: InetAddress? = null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val isP2pName = iface.name.startsWith("p2p", ignoreCase = true)
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        if (isP2pName) {
                            return addr
                        }
                        if (addr.hostAddress?.startsWith("192.168.49.") == true) {
                            fallbackP2pSubnet = addr
                        }
                    }
                }
            }
            fallbackP2pSubnet
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendFiles(
        isGroupOwner: Boolean,
        hostAddress: String,
        files: List<FileItem>,
        onProgress: (List<FileItem>, TransferStats) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        cancelledFileIds.clear()

        val mutableFiles = files.toMutableList()
        val totalBytes = files.sumOf { it.sizeBytes }
        var totalTransferred = 0L

        var lastSampleTime = System.currentTimeMillis()
        var bytesSample = 0L
        var currentSpeed = 0L

        try {
            val socket = openPeerSocket(isGroupOwner = isGroupOwner, groupOwnerAddress = hostAddress)

            val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // Send Magic Header & file count
            outputStream.writeInt(MAGIC_HEADER)
            outputStream.writeInt(files.size)

            // Send metadata for all files
            for (file in files) {
                outputStream.writeUTF(file.id)
                outputStream.writeUTF(file.name)
                outputStream.writeLong(file.sizeBytes)
                outputStream.writeUTF(file.mimeType)
            }
            outputStream.flush()

            // Wait for receiver ACK
            val ack = inputStream.readByte()
            if (ack.toInt() != 1) {
                throw IllegalStateException("Receiver declined transfer")
            }

            val buffer = ByteArray(CHUNK_SIZE)

            for (i in mutableFiles.indices) {
                if (!isRunning.get() || !coroutineContext.isActive) break

                val currentFile = mutableFiles[i]
                if (cancelledFileIds.contains(currentFile.id)) {
                    outputStream.writeByte(CODE_SKIP)
                    outputStream.flush()
                    mutableFiles[i] = currentFile.copy(status = TransferStatus.CANCELLED)
                    onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))
                    continue
                }

                outputStream.writeByte(CODE_DATA)
                outputStream.flush()

                mutableFiles[i] = currentFile.copy(status = TransferStatus.TRANSFERRING)
                onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))

                val uri = currentFile.uriString?.let { Uri.parse(it) }
                val fileInputStream: InputStream? = if (uri != null) {
                    context.contentResolver.openInputStream(uri)
                } else null

                if (fileInputStream == null) {
                    outputStream.writeInt(CODE_SKIP)
                    outputStream.flush()
                    mutableFiles[i] = currentFile.copy(status = TransferStatus.FAILED)
                    continue
                }

                var fileTransferred = 0L
                var fileCancelled = false

                fileInputStream.use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        if (!isRunning.get() || !coroutineContext.isActive) {
                            outputStream.writeInt(CODE_CANCELLED_MID)
                            outputStream.flush()
                            fileCancelled = true
                            break
                        }

                        if (cancelledFileIds.contains(currentFile.id)) {
                            outputStream.writeInt(CODE_CANCELLED_MID)
                            outputStream.flush()
                            fileCancelled = true
                            break
                        }

                        outputStream.writeInt(bytesRead)
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()

                        fileTransferred += bytesRead
                        totalTransferred += bytesRead
                        bytesSample += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSampleTime
                        if (elapsed >= 400) {
                            currentSpeed = (bytesSample * 1000L) / elapsed.coerceAtLeast(1)
                            bytesSample = 0L
                            lastSampleTime = now

                            mutableFiles[i] = currentFile.copy(
                                transferredBytes = fileTransferred,
                                status = TransferStatus.TRANSFERRING
                            )
                            onProgress(
                                mutableFiles.toList(),
                                calculateStats(totalBytes, totalTransferred, currentSpeed)
                            )
                        }
                    }

                    if (!fileCancelled) {
                        outputStream.writeInt(CODE_EOF)
                        outputStream.flush()
                    }
                }

                mutableFiles[i] = currentFile.copy(
                    transferredBytes = fileTransferred,
                    status = if (fileCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
                )
                onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))
            }

            try {
                socket.close()
            } catch (_: Exception) {}
            onComplete(true, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val isExpectedClosure = !isRunning.get() || !coroutineContext.isActive ||
                    (e is SocketException && e.message?.contains("closed", ignoreCase = true) == true)
            if (!isExpectedClosure) {
                Log.e("FileTransferEngine", "Send failed", e)
                onComplete(false, e.localizedMessage)
            }
        } finally {
            isRunning.set(false)
            try {
                clientSocket?.close()
            } catch (_: Exception) {}
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            clientSocket = null
            serverSocket = null
        }
    }

    suspend fun receiveFiles(
        isGroupOwner: Boolean,
        hostAddress: String,
        onProgress: (List<FileItem>, TransferStats) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        cancelledFileIds.clear()

        try {
            val socket = openPeerSocket(isGroupOwner = isGroupOwner, groupOwnerAddress = hostAddress)

            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val magic = inputStream.readInt()
            if (magic != MAGIC_HEADER) {
                throw IllegalStateException("Invalid transfer protocol")
            }

            val count = inputStream.readInt()
            val filesList = mutableListOf<FileItem>()
            for (i in 0 until count) {
                val id = inputStream.readUTF()
                val name = inputStream.readUTF()
                val size = inputStream.readLong()
                val mime = inputStream.readUTF()
                filesList.add(FileItem(id = id, name = name, sizeBytes = size, mimeType = mime))
            }

            val mutableFiles = filesList.toMutableList()
            val totalBytes = filesList.sumOf { it.sizeBytes }
            var totalTransferred = 0L

            var lastSampleTime = System.currentTimeMillis()
            var bytesSample = 0L
            var currentSpeed = 0L

            // Send ACK
            outputStream.writeByte(1)
            outputStream.flush()

            onProgress(mutableFiles.toList(), calculateStats(totalBytes, 0L, 0L))

            val buffer = ByteArray(CHUNK_SIZE)

            for (i in mutableFiles.indices) {
                if (!isRunning.get() || !coroutineContext.isActive) break

                val file = mutableFiles[i]
                val fileCode = inputStream.readByte().toInt()

                if (fileCode == CODE_SKIP) {
                    mutableFiles[i] = file.copy(status = TransferStatus.CANCELLED)
                    onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))
                    continue
                }

                mutableFiles[i] = file.copy(status = TransferStatus.TRANSFERRING)
                onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))

                val destination = createDestination(file.name, file.mimeType)
                var fileTransferred = 0L
                var isCancelled = false

                try {
                    destination.outputStream.use { fos ->
                        while (true) {
                            val chunkSize = inputStream.readInt()
                            if (chunkSize == CODE_EOF) {
                                break
                            }
                            if (chunkSize == CODE_CANCELLED_MID) {
                                isCancelled = true
                                break
                            }

                            if (cancelledFileIds.contains(file.id)) {
                                isCancelled = true
                            }

                            inputStream.readFully(buffer, 0, chunkSize)
                            if (!isCancelled) {
                                fos.write(buffer, 0, chunkSize)
                            }

                            fileTransferred += chunkSize
                            totalTransferred += chunkSize
                            bytesSample += chunkSize

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSampleTime
                            if (elapsed >= 400) {
                                currentSpeed = (bytesSample * 1000L) / elapsed.coerceAtLeast(1)
                                bytesSample = 0L
                                lastSampleTime = now

                                mutableFiles[i] = file.copy(
                                    transferredBytes = fileTransferred,
                                    status = TransferStatus.TRANSFERRING
                                )
                                onProgress(
                                    mutableFiles.toList(),
                                    calculateStats(totalBytes, totalTransferred, currentSpeed)
                                )
                            }
                        }
                    }
                    finalizeDestination(destination, success = !isCancelled)
                } catch (e: Exception) {
                    Log.e("FileTransferEngine", "Error receiving file ${file.name}", e)
                    isCancelled = true
                    finalizeDestination(destination, success = false)
                }

                mutableFiles[i] = file.copy(
                    transferredBytes = fileTransferred,
                    status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.COMPLETED
                )
                onProgress(mutableFiles.toList(), calculateStats(totalBytes, totalTransferred, currentSpeed))
            }

            try {
                socket.close()
            } catch (_: Exception) {}
            onComplete(true, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val isExpectedClosure = !isRunning.get() || !coroutineContext.isActive ||
                    (e is SocketException && e.message?.contains("closed", ignoreCase = true) == true)
            if (!isExpectedClosure) {
                Log.e("FileTransferEngine", "Receive failed", e)
                onComplete(false, e.localizedMessage)
            }
        } finally {
            isRunning.set(false)
            try {
                clientSocket?.close()
            } catch (_: Exception) {}
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            clientSocket = null
            serverSocket = null
        }
    }

    private data class ReceiveDestination(
        val outputStream: OutputStream,
        val mediaUri: Uri? = null,
        val file: File? = null
    )

    internal fun resolveSafeMimeType(fileName: String, rawMime: String): String {
        val trimmed = rawMime.trim()
        if (trimmed.isNotEmpty() && !trimmed.contains('*') && trimmed.contains('/')) {
            return trimmed
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val guessed = if (ext.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        return guessed ?: "application/octet-stream"
    }

    internal fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.replace('\\', '/').substringAfterLast('/').trim()
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") {
            "file_${System.currentTimeMillis()}"
        } else {
            cleaned
        }
    }

    private fun createDestination(fileName: String, mimeType: String): ReceiveDestination {
        val safeName = sanitizeFileName(fileName)
        val safeMime = resolveSafeMimeType(safeName, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver: ContentResolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Mini Share")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    val stream = resolver.openOutputStream(uri)
                    if (stream != null) {
                        return ReceiveDestination(outputStream = stream, mediaUri = uri)
                    }
                    resolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e("FileTransferEngine", "MediaStore insert failed", e)
            }
        }

        val fallback = getFallbackFile(safeName)
        return ReceiveDestination(outputStream = FileOutputStream(fallback), file = fallback)
    }

    private fun finalizeDestination(destination: ReceiveDestination, success: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destination.mediaUri != null) {
            try {
                if (success) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    context.contentResolver.update(destination.mediaUri, values, null, null)
                } else {
                    context.contentResolver.delete(destination.mediaUri, null, null)
                }
            } catch (_: Exception) {}
        } else if (destination.file != null) {
            try {
                if (success) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destination.file.absolutePath),
                        null,
                        null
                    )
                } else {
                    destination.file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    internal fun getFallbackFile(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        val publicMiniShare = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Mini Share"
        )
        val targetDir = try {
            if (!publicMiniShare.exists()) {
                publicMiniShare.mkdirs()
            }
            if (publicMiniShare.exists() && publicMiniShare.canWrite()) {
                publicMiniShare
            } else {
                File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "Mini Share"
                ).apply { mkdirs() }
            }
        } catch (_: Exception) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "Mini Share"
            ).apply { mkdirs() }
        }

        var target = File(targetDir, safeName)
        var count = 1
        val baseName = safeName.substringBeforeLast(".")
        val extension = safeName.substringAfterLast(".", "")
        while (target.exists()) {
            val newName = if (extension.isNotEmpty() && safeName.contains('.')) {
                "$baseName($count).$extension"
            } else {
                "$baseName($count)"
            }
            target = File(targetDir, newName)
            count++
        }
        return target
    }

    private fun calculateStats(totalBytes: Long, transferredBytes: Long, speed: Long): TransferStats {
        val effectiveTotal = totalBytes.coerceAtLeast(transferredBytes)
        val safeTransferred = transferredBytes.coerceAtMost(effectiveTotal)
        val progress = if (effectiveTotal > 0) (safeTransferred.toFloat() / effectiveTotal).coerceIn(0f, 1f) else 0f
        val remainingBytes = (effectiveTotal - safeTransferred).coerceAtLeast(0)
        val remainingSeconds = if (speed > 0) remainingBytes / speed else 0L

        return TransferStats(
            totalBytes = effectiveTotal,
            transferredBytes = safeTransferred,
            speedBytesPerSec = speed,
            remainingSeconds = remainingSeconds,
            overallProgress = progress
        )
    }

    private fun resolveLocalPeerAddress(hostAddress: String): InetAddress {
        val parts = hostAddress.trim().split('.')
        require(parts.size == 4) { "Invalid local peer address" }
        val bytes = ByteArray(4) { index ->
            val segment = parts[index].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid local peer address")
            require(segment in 0..255) { "Invalid local peer address" }
            segment.toByte()
        }
        val address = InetAddress.getByAddress(bytes)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) {
            "Only local Wi-Fi Direct peer addresses are allowed"
        }
        return address
    }
}
