package com.example.myapplication_ttt

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication_ttt.ui.theme.MyApplicationtttTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.io.File
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationtttTheme {
                VideoPlayerApp()
            }
        }
    }
}

data class VideoMarker(
    val timeMs: Long, // 时间点（毫秒）
    val label: String // 标签（可选）
)

data class VideoMarkers(
    val videoUri: String, // 视频的 URI 或唯一标识
    val markers: List<VideoMarker> // 标记的时间点列表
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerApp() {
    var videoUrl by remember { mutableStateOf<Uri?>(null) }
    val markers = remember { mutableStateListOf<VideoMarker>() }
    val context = LocalContext.current

    // File picker
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            videoUrl = uri // Update videoUrl
            markers.clear()
            if (uri != null) {
                // Load marker data
                markers.addAll(loadMarkersFromFile(context, uri))
            }
        }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("影子跟读播放器") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {


            // Upload video button
            Button(
                onClick = { pickVideoLauncher.launch("video/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择视频")
            }

            // Display the latest added marker's time point
            if (markers.isNotEmpty()) {
                val latestMarker = markers.last()
                Text(text = "最新添加的标记点: ${latestMarker.label}")
            }

            // Display video player
            videoUrl?.let { url ->
                VideoPlayerWithTime(
                    videoUrl = url,
                    markers = markers,
                    onAddMarker = { timeMs ->
                        markers.add(VideoMarker(timeMs, "标记了 ${formatTime(timeMs)}"))
                        // Save marker data
                        saveMarkersToFile(context, url, markers)
                        // Reload markers from file
                        val updatedMarkers = loadMarkersFromFile(context, url)
                        markers.clear()
                        markers.addAll(updatedMarkers)
                    },
                    onDeleteMarker = { timeMs ->
                        markers.removeAll { it.timeMs == timeMs }
                        // Save updated marker data
                        saveMarkersToFile(context, url, markers)
                        // Reload markers from file
                        val updatedMarkers = loadMarkersFromFile(context, url)
                        markers.clear()
                        markers.addAll(updatedMarkers)
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerWithTime(
    videoUrl: Uri,
    markers: List<VideoMarker>,
    onAddMarker: (Long) -> Unit,
    onDeleteMarker: (Long) -> Unit // Callback function to delete a marker
) {
    val context = LocalContext.current

    // Create ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // Listen for videoUrl changes and update ExoPlayer's media item
    LaunchedEffect(videoUrl) {
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    // Current playback position (milliseconds)
    var currentPosition by remember { mutableStateOf(0L) }

    // Launch a coroutine to periodically update the current playback position
    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition // Get current playback position
            delay(100) // Update every 100 milliseconds
        }
    }

    // Playback speed options
    val playbackSpeeds = listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.2f)
    var expanded by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(1.0f) }

    // Looping state
    var isLooping by remember { mutableStateOf(false) }
    var startMarker by remember { mutableStateOf(0L) }
    var endMarker by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Embed PlayerView using AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true // Enable default controller
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f) // Adjust the aspect ratio as needed
        )

        // Display current playback position
        Text(text = "当前时间点: ${formatTime(currentPosition)}")

        // Grid layout for buttons
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val previousMarker = markers.lastOrNull { it.timeMs < currentPosition }
                Button(onClick = {
                    previousMarker?.let { exoPlayer.seekTo(it.timeMs) }
                }) {
                    Text("上: ${previousMarker?.let { formatTime(it.timeMs) } ?: "无"}")
                }
            }
            item {
                Button(onClick = { onAddMarker(currentPosition) }) {
                    Text("添加标记")
                }
            }
            item {
                val nextMarker = markers.firstOrNull { it.timeMs > currentPosition }
                Button(onClick = {
                    nextMarker?.let { exoPlayer.seekTo(it.timeMs) }
                }) {
                    Text("下: ${nextMarker?.let { formatTime(it.timeMs) } ?: "无"}")
                }
            }
            item {
                Button(onClick = {
                    if (!exoPlayer.isPlaying) {
                        val markerToDelete = markers.find { it.timeMs == currentPosition }
                        markerToDelete?.let { onDeleteMarker(it.timeMs) }
                    }
                }) {
                    Text("删除标记")
                }
            }
            item {
                Button(onClick = {
                    val previousMarker = markers.lastOrNull { it.timeMs <= currentPosition }
                    val nextMarker = markers.firstOrNull { it.timeMs > currentPosition }
                    if (previousMarker != null && nextMarker != null) {
                        startMarker = previousMarker.timeMs
                        endMarker = nextMarker.timeMs
                        isLooping = !isLooping
                    }
                }) {
                    Text(
                        if (isLooping)
                            "${formatTime(startMarker)} - ${formatTime(endMarker)}"
                        else
                            "循环播放off"
                    )
                }
            }
            item {
                Box {
                    Button(onClick = { expanded = true }) {
                        Text("倍速: ${selectedSpeed}x")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        playbackSpeeds.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("$speed x") },
                                onClick = {
                                    selectedSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Display all markers' timeMs
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(markers) { marker ->
                Text(text = "标记时间点: ${formatTime(marker.timeMs)}")
            }
        }
    }

    LaunchedEffect(isLooping, startMarker, endMarker) {
        while (isLooping) {
            if (exoPlayer.currentPosition >= endMarker) {
                exoPlayer.seekTo(startMarker)
                exoPlayer.play()
            }
            delay(100) // 每 100 毫秒检查一次播放位置
        }
    }

    exoPlayer.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && isLooping) {
                if (exoPlayer.currentPosition < startMarker) {
                    exoPlayer.seekTo(startMarker)
                }
            }
        }
    })


    // Release ExoPlayer when the Composable exits
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}

// 格式化时间（HH:MM:SS）
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = (milliseconds / (1000 * 60 * 60)) % 24
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

// 保存标记数据到文件
fun saveMarkersToFile(context: Context, videoUri: Uri, markers: List<VideoMarker>) {
    // 对标记按时间点排序
    val sortedMarkers = markers.sortedBy { it.timeMs }

    val videoMarkers = VideoMarkers(videoUri.toString(), sortedMarkers)
    val json = Gson().toJson(videoMarkers)

    // 获取标记文件的路径
    val fileName = "${getVideoId(videoUri)}.markers.json"
    val file = File(context.filesDir, fileName)

    // 将 JSON 写入文件
    file.writeText(json)
}

// 从文件加载标记数据
fun loadMarkersFromFile(context: Context, videoUri: Uri): List<VideoMarker> {
    // 获取标记文件的路径
    val fileName = "${getVideoId(videoUri)}.markers.json"
    val file = File(context.filesDir, fileName)

    // 如果文件不存在，返回空列表
    if (!file.exists()) return emptyList()

    // 读取文件内容并反序列化为对象
    val json = file.readText()
    val videoMarkers = Gson().fromJson(json, VideoMarkers::class.java)
    return videoMarkers.markers
}

// 生成视频的唯一标识
fun getVideoId(videoUri: Uri): String {
    val input = videoUri.toString().toByteArray()
    val md = MessageDigest.getInstance("MD5")
    val hashBytes = md.digest(input)
    return hashBytes.joinToString("") { "%02x".format(it) }
}