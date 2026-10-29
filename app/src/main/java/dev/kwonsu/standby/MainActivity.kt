package dev.kwonsu.standby

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var overlayAllowed by mutableStateOf(false)
    private var listenerAllowed by mutableStateOf(false)
    private var notice by mutableStateOf("")
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMonitoring() else {
            notice = "자동 스탠바이 상태와 종료 버튼을 표시하려면 알림을 허용해 주세요."
            Diagnostics.record("notification_denied", "자동 스탠바이 시작하지 않음")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            FoldTheme {
                val running by MonitorRuntime.running.collectAsStateWithLifecycle()
                val status by MonitorRuntime.status.collectAsStateWithLifecycle()
                val events by Diagnostics.events.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("FOLD / STANDBY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("놓아두면, 시계.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        Text("Fold6 스탠바이 · ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Section("나만의 스탠바이") {
                            Text("글꼴과 색상, 배경 사진, 움직이는 GIF까지. 시계와 버튼을 원하는 자리에 놓아보세요.")
                            Button(onClick = { startActivity(Intent(this@MainActivity, EditorActivity::class.java)) }) { Text("화면 꾸미기") }
                        }
                        Section("잠그고, 충전하고, 가로로") {
                            Text(status, style = MaterialTheme.typography.titleMedium)
                            Text("화면이 잠겨 있고, 충전기가 연결되어 있고, 가로로 놓였을 때 시계가 나타납니다.")
                            Text("잠금을 풀거나, 충전기를 빼거나, 세로로 돌리면 자동으로 사라집니다.")
                            Text(if (overlayAllowed) "자동 실행 권한 허용됨" else "자동 실행 권한을 먼저 허용해 주세요.", color = MaterialTheme.colorScheme.primary)
                            if (!overlayAllowed) OutlinedButton(onClick = {
                                openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                            }) { Text("자동 실행 권한 설정") }
                            Text("이미 충전 중이어도 괜찮습니다. 아래에서 켜 둔 뒤 화면을 잠그고 가로로 놓으세요.", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                if (running) stopService(Intent(this@MainActivity, ChargeMonitorService::class.java)) else requestMonitoring()
                            }) { Text(if (running) "자동 스탠바이 끄기" else "자동 스탠바이 켜기") }
                        }
                        Section("알림 패널") {
                            Text("시계 화면을 오른쪽에서 왼쪽으로 쓸면 우측 절반에 알림이 나타납니다. 목록 안에서는 위아래로 스크롤하세요.")
                            Text(if (listenerAllowed) "알림 접근 허용됨" else "기기의 알림을 표시하려면 알림 접근을 허용해 주세요.", color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = ::openNotificationAccess) { Text(if (listenerAllowed) "알림 접근 설정" else "알림 접근 허용") }
                            OutlinedButton(onClick = { startActivity(Intent(this@MainActivity, NotificationSettingsActivity::class.java)) }) { Text("앱별 알림 규칙") }
                            Text("선택한 앱의 알림을 최근 500개까지 기기 내부에 암호화해 보관합니다. 앱별 묶음을 누르면 펼쳐지고, 개별 알림을 왼쪽으로 밀면 스탠바이 기록에서만 제거합니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        Section("접어도 이어서 보기") {
                            Text("설정 → 디스플레이 → 커버 화면에서 앱 계속 사용에서 이 앱을 허용하면 닫은 뒤에도 이어서 시험할 수 있습니다.")
                            Text("보안 잠금은 유지됩니다. 앱을 강제 종료하거나 기기를 재부팅한 뒤에는 자동 스탠바이를 다시 켜세요.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (notice.isNotEmpty()) Text(notice, color = MaterialTheme.colorScheme.tertiary)
                        Section("최근 진단 기록") {
                            Text("잠금·충전·가로 자세와 실제 화면 표시를 기록합니다.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = ::shareDiagnostics) { Text("진단 기록 내보내기") }
                            events.take(6).forEach { event ->
                                Text("${event.event}\n${event.details}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayAllowed = Settings.canDrawOverlays(this)
        listenerAllowed = NotificationRuntime.isAllowed(this)
        NotificationRuntime.refreshAccess(this)
    }

    private fun openNotificationAccess() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationRuntime.component(this).flattenToString()))
        }.onFailure { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun requestMonitoring() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startMonitoring()
    }

    private fun startMonitoring() {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(ChargeMonitorService.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            notice = "앱 알림 또는 ‘자동 스탠바이’ 채널을 허용한 뒤 다시 켜주세요."
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            return
        }
        runCatching { ContextCompat.startForegroundService(this, Intent(this, ChargeMonitorService::class.java)) }
            .onFailure { notice = "감시 시작 실패: ${it.javaClass.simpleName}"; Diagnostics.record("monitor_start_failed", notice) }
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { notice = "이 기기에서 설정 화면을 열지 못했습니다. 시스템 설정에서 직접 선택해주세요." }
    }

    private fun shareDiagnostics() {
        val file = Diagnostics.export(this)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "진단 기록 내보내기"))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
