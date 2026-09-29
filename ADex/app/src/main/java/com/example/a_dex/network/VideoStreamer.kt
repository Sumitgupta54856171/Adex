package com.example.a_dex.network

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

class VideoStreamer {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var mediaCodec: MediaCodec? = null
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null

    private val streamLock = Any()
    private val streamerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null

    private val frameChannel = Channel<ByteArray>(
        capacity = 60,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile
    private var spsPpsHeader: ByteArray? = null

    @Volatile
    private var totalFrameCount = 0L

    var inputSurface: Surface? = null
        private set

    @Volatile var isStreaming = false

    suspend fun startServer() = withContext(Dispatchers.IO) {
        isStreaming = true
        startSocketSender()
        startEncoding() // Create the surface and MediaCodec encoder
        try {
            serverSocket = ServerSocket(8765)
            Timber.d("Video Server listening on 8765")
            while (isStreaming) {
                val client = serverSocket?.accept() ?: break
                Timber.d("TV Client connected for video!")

                synchronized(streamLock) {
                    try { clientSocket?.close() } catch (e: Exception) {}
                    clientSocket = client
                    val stream = DataOutputStream(client.getOutputStream())
                    outputStream = stream

                    // Send SPS/PPS header first if captured
                    spsPpsHeader?.let { header ->
                        try {
                            stream.writeInt(header.size)
                            stream.write(header)
                            stream.flush()
                            Timber.d("Sent cached SPS/PPS header (${header.size} bytes) to new client")
                        } catch (e: Exception) {
                            Timber.e("Failed to send SPS/PPS header: ${e.toString()}\n${e.stackTraceToString()}")
                        }
                    }
                }

                // Request an I-Frame so TV client renders immediately
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                mediaCodec?.setParameters(params)
            }
        } catch (e: Exception) {
            Timber.e("Video server error: ${e.toString()}\n${e.stackTraceToString()}")
        }
    }

    private fun startSocketSender() {
        streamingJob?.cancel()
        streamingJob = streamerScope.launch {
            for (data in frameChannel) {
                if (!isStreaming) break
                sendNalUnit(data)
            }
        }
    }

    private fun startEncoding() {
        try {
            val thread = HandlerThread("EncoderThread")
            thread.start()
            encoderThread = thread
            val handler = Handler(thread.looper)
            encoderHandler = handler

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger("prepend-sps-pps-to-idr-frames", 1)
                // Repeat previous frame every 50ms so static screens keep producing frames
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000L)
            }
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    val buffer = codec.getOutputBuffer(index) ?: return
                    if (info.size > 0) {
                        val data = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(data)

                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            synchronized(streamLock) {
                                spsPpsHeader = data
                            }
                            Timber.d("Codec config (SPS/PPS) captured: ${info.size} bytes")
                        } else {
                            frameChannel.trySend(data)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Timber.e("MediaCodec Error: ${e.toString()}\n${e.stackTraceToString()}")
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            }, handler)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec?.createInputSurface()
            mediaCodec?.start()
        } catch (e: Exception) {
            Timber.e("Encoding error: ${e.toString()}\n${e.stackTraceToString()}")
        }
    }

    private fun sendNalUnit(data: ByteArray) {
        synchronized(streamLock) {
            val client = clientSocket
            val stream = outputStream
            if (client == null || client.isClosed || stream == null) return

            try {
                stream.writeInt(data.size)
                stream.write(data)
                stream.flush()

                totalFrameCount++
                if (totalFrameCount % 60 == 0L) {
                    Timber.d("Streaming active: Sent frame #$totalFrameCount (${data.size} bytes) to TV client")
                }
            } catch (e: Exception) {
                Timber.e("Client write error: ${e.toString()}\n${e.stackTraceToString()}")
                try { client.close() } catch (ex: Exception) {}
                clientSocket = null
                outputStream = null
            }
        }
    }

    fun stopServer() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null
        try { mediaCodec?.stop() } catch (e: Exception) {}
        try { mediaCodec?.release() } catch (e: Exception) {}
        mediaCodec = null
        inputSurface?.release()
        inputSurface = null
        try { encoderThread?.quitSafely() } catch (e: Exception) {}
        encoderThread = null
        encoderHandler = null
        synchronized(streamLock) {
            try { serverSocket?.close() } catch (e: Exception) {}
            try { clientSocket?.close() } catch (e: Exception) {}
            outputStream = null
            clientSocket = null
        }
    }
}
