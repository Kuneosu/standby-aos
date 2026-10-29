package dev.kwonsu.standby

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val settings = SettingsStore(this)
        setContent {
            val rules by settings.notificationRules.collectAsStateWithLifecycle(initialValue = null)
            val feed by NotificationRuntime.state.collectAsStateWithLifecycle()
            val installed by produceState<List<NotificationApp>>(emptyList()) {
                value = withContext(Dispatchers.IO) {
                    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    packageManager.queryIntentActivities(launcher, 0).map {
                        NotificationApp(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(packageManager).toString())
                    }.distinctBy { it.packageName }
                }
            }
            val current = rules
            val apps = remember(installed, feed.apps, current) {
                val fallback = (current?.apps?.keys.orEmpty() + current?.selectedApps.orEmpty()).map { NotificationApp(it, it) }
                val known = familiarNotificationApps + feed.apps + installed + fallback
                val collator = Collator.getInstance(Locale.KOREAN)
                known.distinctBy { it.packageName }.filter { it.packageName != packageName }
                    .sortedWith(compareBy<NotificationApp> { if (it.packageName in initialNotificationRules) 0 else 1 }
                        .thenComparator { a, b -> collator.compare(a.name, b.name) })
            }
            val scope = rememberCoroutineScope()
            var saving by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            fun save(change: suspend () -> Unit) {
                scope.launch {
                    saving = true
                    try { change(); error = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "규칙을 저장하지 못했습니다. 다시 선택해 주세요." }
                    finally { saving = false }
                }
            }
            FoldTheme {
                if (current == null) Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else NotificationRulesScreen(current, apps, saving, error, onBack = ::finish,
                    onMode = { save { settings.setNotificationSelectionMode(it) } },
                    onSelect = { packageName, selected -> save { settings.setNotificationAppSelected(packageName, selected) } },
                    onRule = { packageName, rule -> save { settings.setNotificationRule(packageName, rule) } })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationRuntime.refreshAccess(this)
    }
}

@Composable
internal fun NotificationRulesScreen(rules: NotificationRules, apps: List<NotificationApp>, saving: Boolean,
    error: String?, onBack: () -> Unit, onMode: (Boolean) -> Unit, onSelect: (String, Boolean) -> Unit,
    onRule: (String, NotificationRule) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(apps, query) { apps.filter { it.name.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) } }
    Surface(Modifier.fillMaxSize().testTag("notification_rules_screen")) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(Modifier.safeDrawingPadding().fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("스탠바이 알림 규칙", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onBack, Modifier.testTag("close_notification_rules")) { Text("닫기") }
                }
                Text("이 스탠바이 화면에만 적용됩니다. 변경하면 바로 저장됩니다.", Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !rules.selectedOnly, onClick = { onMode(false) }, enabled = !saving,
                        label = { Text("전체 앱") }, modifier = Modifier.testTag("notification_mode_all"))
                    FilterChip(selected = rules.selectedOnly, onClick = { onMode(true) }, enabled = !saving,
                        label = { Text("선택한 앱만") }, modifier = Modifier.testTag("notification_mode_selected"))
                }
                Text(if (rules.selectedOnly) "체크한 앱만 표시합니다. ${rules.selectedApps.count { rules.storedRuleFor(it) != NotificationRule.HIDDEN }}개 선택됨"
                    else "전체 앱을 표시하되, 숨김으로 정한 앱은 제외합니다.", Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("내용 표시를 골라도 Android나 원래 앱에서 가려서 보낸 내용은 표시할 수 없습니다.", Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("notification_app_search"),
                    label = { Text("앱 검색") }, singleLine = true)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (shown.isEmpty()) Text("검색 결과가 없습니다.", Modifier.padding(vertical = 24.dp))
                LazyColumn(Modifier.weight(1f).testTag("notification_rule_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        val checked = app.packageName in rules.selectedApps && rules.storedRuleFor(app.packageName) != NotificationRule.HIDDEN
                        val interaction = if (rules.selectedOnly) Modifier.toggleable(value = checked, enabled = !saving, role = Role.Checkbox) {
                            onSelect(app.packageName, it)
                        } else Modifier.clickable(enabled = !saving) { selectedPackage = app.packageName }
                        Row(Modifier.fillMaxWidth().then(interaction)
                            .testTag("notification_rule_${app.packageName}").padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (rules.selectedOnly) Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (rules.selectedOnly && !checked) "선택 안 함" else rules.ruleFor(app.packageName).label,
                                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                                    color = if (rules.ruleFor(app.packageName) == NotificationRule.HIDDEN) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { selectedPackage = app.packageName }, enabled = !saving,
                                modifier = Modifier.testTag("notification_content_${app.packageName}")) {
                                Text(if (rules.selectedOnly) "내용 설정" else "변경", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
    val selected = apps.firstOrNull { it.packageName == selectedPackage }
    if (selected != null) AlertDialog(onDismissRequest = { selectedPackage = null }, title = { Text(selected.name) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            NotificationRule.entries.forEach { rule ->
                Row(Modifier.fillMaxWidth().selectable(selected = rules.storedRuleFor(selected.packageName) == rule, role = Role.RadioButton) {
                    onRule(selected.packageName, rule); selectedPackage = null
                }.testTag("choose_rule_${rule.name}").padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = rules.storedRuleFor(selected.packageName) == rule, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(rule.label, style = MaterialTheme.typography.bodyLarge)
                        Text(rule.description, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { selectedPackage = null }) { Text("취소") } })
}
