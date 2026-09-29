package com.pedro.rtpstreamer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var liveGlView: OpenGlView
    private lateinit var btnStream: Button
    private lateinit var btnRecord: Button
    private lateinit var btnFlipCamera: ImageButton
    private lateinit var btnMuteMic: ImageButton
    private lateinit var etRtmpUrl: EditText
    private lateinit var tvLiveIndicator: TextView
    private lateinit var tvBitrate: TextView
    private lateinit var tvFps: TextView
    private lateinit var tvDuration: TextView
    private lateinit var sbMicVolume: SeekBar

    private var rtmpCamera: RtmpCamera2? = null
    private var isRecording = false
    private var recordPath = ""
    private var streamStartTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initCamera()
        setupListeners()
    }

    private fun initViews() {
        liveGlView = findViewById(R.id.liveGlView)
        btnStream = findViewById(R.id.btnStream)
        btnRecord = findViewById(R.id.btnRecord)
        btnFlipCamera = findViewById(R.id.btnFlipCamera)
        btnMuteMic = findViewById(R.id.btnMuteMic)
        etRtmpUrl = findViewById(R.id.etRtmpUrl)
        tvLiveIndicator = findViewById(R.id.tvLiveIndicator)
        tvBitrate = findViewById(R.id.tvBitrate)
        tvFps = findViewById(R.id.tvFps)
        tvDuration = findViewById(R.id.tvDuration)
        sbMicVolume = findViewById(R.id.sbMicVolume)

        // Mock Scene & Source List
        val lvScenes = findViewById<ListView>(R.id.lvScenes)
        val lvSources = findViewById<ListView>(R.id.lvSources)
        lvScenes.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("Main Scene", "IRL Camera", "Drone Cam"))
        lvSources.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("Camera Input", "Overlay Logo", "Mic Audio"))
    }

    private fun initCamera() {
        rtmpCamera = RtmpCamera2(liveGlView, this)
        if (hasPermissions()) {
            startPreview()
        } else {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    private fun startPreview() {
        if (rtmpCamera?.isOnPreview == false) {
            rtmpCamera?.startPreview(1280, 720)
        }
    }

    private fun setupListeners() {
        btnFlipCamera.setOnClickListener {
            try {
                rtmpCamera?.switchCamera()
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak dapat menukar kamera", Toast.LENGTH_SHORT).show()
            }
        }

        btnMuteMic.setOnClickListener {
            rtmpCamera?.let {
                if (it.isAudioMuted) {
                    it.unMuteAudio()
                    Toast.makeText(this, "Microphone Aktif", Toast.LENGTH_SHORT).show()
                } else {
                    it.muteAudio()
                    Toast.makeText(this, "Microphone Dimute", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStream.setOnClickListener {
            val url = etRtmpUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Masukkan URL RTMP Server", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            rtmpCamera?.let { camera ->
                if (!camera.isStreaming) {
                    if (camera.prepareAudio() && camera.prepareVideo(1280, 720, 30, 2500 * 1024, 0)) {
                        camera.startStream(url)
                        btnStream.text = "STOP STREAM"
                        btnStream.setBackgroundColor(0xFF27272A.toInt())
                        tvLiveIndicator.text = "● LIVE"
                        tvLiveIndicator.setTextColor(0xFFEF4444.toInt())
                        startTimer()
                    } else {
                        Toast.makeText(this, "Gagal menginisialisasi hardware encoder", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    camera.stopStream()
                    btnStream.text = "START STREAM"
                    btnStream.setBackgroundColor(0xFFDC2626.toInt())
                    tvLiveIndicator.text = "● OFFLINE"
                    tvLiveIndicator.setTextColor(0xFF71717A.toInt())
                    stopTimer()
                }
            }
        }

        btnRecord.setOnClickListener {
            rtmpCamera?.let { camera ->
                if (!isRecording) {
                    val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val file = File(folder, "AERO_$timeStamp.mp4")
                    recordPath = file.absolutePath

                    if (!camera.isStreaming) {
                        if (!camera.prepareAudio() || !camera.prepareVideo(1280, 720, 30, 2500 * 1024, 0)) {
                            Toast.makeText(this, "Gagal menyiapkan encoder rekaman", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    }
                    camera.startRecord(recordPath)
                    isRecording = true
                    btnRecord.text = "STOP REC"
                    btnRecord.setBackgroundColor(0xFFDC2626.toInt())
                    Toast.makeText(this, "Merekam ke memori...", Toast.LENGTH_SHORT).show()
                } else {
                    camera.stopRecord()
                    isRecording = false
                    btnRecord.text = "START REC"
                    btnRecord.setBackgroundColor(0xFF3F3F46.toInt())
                    Toast.makeText(this, "Tersimpan: $recordPath", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startTimer() {
        streamStartTime = System.currentTimeMillis()
        timerHandler.post(object : Runnable {
            override fun run() {
                val millis = System.currentTimeMillis() - streamStartTime
                val seconds = (millis / 1000) % 60
                val minutes = (millis / (1000 * 60)) % 60
                val hours = (millis / (1000 * 60 * 60))
                tvDuration.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun stopTimer() {
        timerHandler.removeCallbacksAndMessages(null)
        tvDuration.text = "00:00:00"
        tvBitrate.text = "0 kbps"
    }

    private fun hasPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && hasPermissions()) {
            startPreview()
        }
    }

    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {
        runOnUiThread { Toast.makeText(this, "Siaran Terhubung ke RTMP Server!", Toast.LENGTH_SHORT).show() }
    }
    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Gagal: $reason", Toast.LENGTH_SHORT).show()
            rtmpCamera?.stopStream()
            btnStream.text = "START STREAM"
            btnStream.setBackgroundColor(0xFFDC2626.toInt())
            tvLiveIndicator.text = "● OFFLINE"
            tvLiveIndicator.setTextColor(0xFF71717A.toInt())
            stopTimer()
        }
    }
    override fun onNewBitrate(bitrate: Long) {
        runOnUiThread { tvBitrate.text = "${bitrate / 1000} kbps" }
    }
    override fun onDisconnect() {
        runOnUiThread { Toast.makeText(this, "Siaran Terputus", Toast.LENGTH_SHORT).show() }
    }
    override fun onAuthError() {
        runOnUiThread { Toast.makeText(this, "Autentikasi Ditolak", Toast.LENGTH_SHORT).show() }
    }
    override fun onAuthSuccess() {}
}
