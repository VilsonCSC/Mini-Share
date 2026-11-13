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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class FileTransferEngine(private val context: Context) {

    companion object {
        const val PORT = 8888
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
        private const val MAGIC_HEADER = 0x50325046 // 'P2PF'
        private const val CODE_DATA = 1
        private const val CODE_SKIP = 2
        private const val CODE_CANCELLED_MID = -1
        private const val CODE_EOF = 0
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

    suspend fun sendFiles(
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
            val socket = Socket()
            socket.bind(null)
            socket.connect(InetSocketAddress(hostAddress, PORT), 15000)
            clientSocket = socket

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
                    // Mark file skipped
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
            clientSocket = null
        }
    }

    suspend fun receiveFiles(
        onProgress: (List<FileItem>, TransferStats) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        cancelledFileIds.clear()

        try {
            serverSocket = ServerSocket(PORT).apply {
                reuseAddress = true
                soTimeout = 120000 // 2 minutes wait for sender
            }

            val socket = serverSocket!!.accept()
            clientSocket = socket

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

                val outputStreamForFile = createDestinationOutputStream(file.name, file.mimeType)
                var fileTransferred = 0L
                var isCancelled = false

                try {
                    outputStreamForFile.use { fos ->
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
                } catch (e: Exception) {
                    Log.e("FileTransferEngine", "Error receiving file ${file.name}", e)
                    isCancelled = true
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
            try {
                serverSocket?.close()
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

    private fun createDestinationOutputStream(fileName: String, mimeType: String): OutputStream {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver: ContentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri) ?: FileOutputStream(getFallbackFile(fileName))
            } else {
                FileOutputStream(getFallbackFile(fileName))
            }
        } else {
            FileOutputStream(getFallbackFile(fileName))
        }
    }

    private fun getFallbackFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        var target = File(downloadsDir, fileName)
        var count = 1
        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "")
        while (target.exists()) {
            val newName = if (extension.isNotEmpty()) "$baseName($count).$extension" else "$baseName($count)"
            target = File(downloadsDir, newName)
            count++
        }
        return target
    }

    private fun calculateStats(totalBytes: Long, transferredBytes: Long, speed: Long): TransferStats {
        val safeTransferred = transferredBytes.coerceAtMost(totalBytes)
        val progress = if (totalBytes > 0) (safeTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val remainingBytes = (totalBytes - safeTransferred).coerceAtLeast(0)
        val remainingSeconds = if (speed > 0) remainingBytes / speed else 0L

        return TransferStats(
            totalBytes = totalBytes,
            transferredBytes = safeTransferred,
            speedBytesPerSec = speed,
            remainingSeconds = remainingSeconds,
            overallProgress = progress
        )
    }
}
