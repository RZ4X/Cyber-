package com.example.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ParallelThreadDownloader {

    suspend fun executeDownload(targetUrl: String, destinationFile: File, totalThreads: Int, onProgressUpdate: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val urlConnection = URL(targetUrl).openConnection() as HttpURLConnection
            urlConnection.requestMethod = "HEAD"
            val fileLength = urlConnection.contentLengthLong
            urlConnection.disconnect()

            val outputAllocationFile = RandomAccessFile(destinationFile, "rw")
            outputAllocationFile.setLength(fileLength)
            outputAllocationFile.close()

            val partChunkSize = fileLength / totalThreads
            var dynamicDownloadedBytes = 0L

            val executionWorkers = List(totalThreads) { index ->
                val workerStartByte = index * partChunkSize
                val workerEndByte = if (index == totalThreads - 1) fileLength - 1 else workerStartByte + partChunkSize - 1
                
                launch {
                    try {
                        val connection = URL(targetUrl).openConnection() as HttpURLConnection
                        connection.setRequestProperty("Range", "bytes=$workerStartByte-$workerEndByte")
                        
                        val inputStream = connection.inputStream
                        val randomAccessFile = RandomAccessFile(destinationFile, "rw")
                        randomAccessFile.seek(workerStartByte)

                        val contentBuffer = ByteArray(4096)
                        var readBytesLength: Int
                        
                        while (inputStream.read(contentBuffer).also { readBytesLength = it } != -1) {
                            randomAccessFile.write(contentBuffer, 0, readBytesLength)
                            synchronized(this@ParallelThreadDownloader) {
                                dynamicDownloadedBytes += readBytesLength
                                onProgressUpdate(dynamicDownloadedBytes, fileLength)
                            }
                        }
                        randomAccessFile.close()
                        inputStream.close()
                        connection.disconnect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            executionWorkers.forEach { it.join() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
