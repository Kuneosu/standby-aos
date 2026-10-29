package dev.kwonsu.standby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun NotificationPanel(feed: NotificationFeedState, backdrop: GraphicsLayer, origin: Offset, night: Boolean,
    onClose: () -> Unit, modifier: Modifier = Modifier, onDismiss: (String) -> Unit = NotificationRuntime::dismiss,
    onDismissMany: (Set<String>) -> Unit = NotificationRuntime::dismissMany,
) {
    val glass = rememberGraphicsLayer()
    val foreground = if (night) Color(0xFFE28A7C) else Color(0xFFF4F6F5)
    val groups = remember(feed.items) { groupNotifications(feed.items) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var viewportHeight by remember { mutableIntStateOf(1) }
    Box(modifier.testTag("notification_panel").semantics { paneTitle = "알림"; dismiss { onClose(); true } }
        .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.24f), spotColor = Color.Black.copy(alpha = 0.3f))
        .drawWithContent {
            val corner = CornerRadius(24.dp.toPx())
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
            val blur = 20.dp.toPx()
            val padding = ceil(blur * 2).toInt()
            glass.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            glass.record(size = IntSize(size.width.roundToInt() + padding * 2, size.height.roundToInt() + padding * 2)) {
                translate(padding - origin.x, padding - origin.y) { drawLayer(backdrop) }
            }
            clipPath(path) {
                translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(glass) }
                drawRect(Color.Black.copy(alpha = if (night) 0.52f else 0.32f))
                drawRect(Color.White.copy(alpha = 0.045f))
                this@drawWithContent.drawContent()
            }
            val edge = 0.65.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(edge / 2, edge / 2),
                Size((size.width - edge).coerceAtLeast(0f), (size.height - edge).coerceAtLeast(0f)), cornerRadius = corner, style = Stroke(edge))
        }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("알림", color = foreground, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClose, Modifier.size(44.dp).testTag("close_notifications").semantics { contentDescription = "알림 닫기" }) {
                    Canvas(Modifier.size(18.dp)) {
                        val line = 1.6.dp.toPx()
                        drawLine(foreground.copy(alpha = 0.7f), Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), line, StrokeCap.Round)
                        drawLine(foreground.copy(alpha = 0.7f), Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), line, StrokeCap.Round)
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            if (feed.permissionGranted && feed.connected && feed.items.isNotEmpty()) Row(
                Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${feed.items.size}개의 알림", color = foreground.copy(alpha = 0.45f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                TextButton({ onDismissMany(feed.items.map { it.key }.toSet()) },
                    Modifier.testTag("clear_all_notifications").semantics { contentDescription = "표시된 알림 모두 지우기" },
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("모두 지우기", color = foreground.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
            feed.historyWarning?.let { Text(it, Modifier.padding(vertical = 8.dp), color = Color(0xFFE6B6A7), fontSize = 12.sp) }
            val empty = when {
                !feed.permissionGranted -> "알림 접근을 켜 주세요" to "잠금을 푼 뒤 Standby Screen의 ‘알림 접근 허용’에서 설정할 수 있습니다."
                !feed.connected || feed.unavailable -> "알림 연결 중" to "알림 접근 설정을 확인하면 다시 연결됩니다."
                feed.items.isEmpty() -> "새 알림이 없습니다" to "선택한 앱의 알림이 도착하면 여기에 쌓입니다."
                else -> null
            }
            AnimatedContent(empty, Modifier.weight(1f), transitionSpec = {
                fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(140))
            }, label = "알림 상태 전환") { message ->
            if (message != null) Box(Modifier.fillMaxSize().padding(20.dp).testTag("notification_empty"), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message.first, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(message.second, color = foreground.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            } else LazyColumn(Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }.testTag("notification_list"), contentPadding = PaddingValues(bottom = 14.dp)) {
                items(groups, key = { it.packageName }, contentType = { "group" }) { group ->
                    val open = expanded[group.packageName] == true
                    Column(Modifier.fillMaxWidth().testTag("notification_group_block_${group.packageName}")
                        .animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = tween(180))) {
                        NotificationGroupHeader(group, foreground, open,
                            onClear = { onDismissMany(group.items.map { it.key }.toSet()) }, onToggle = { expanded[group.packageName] = !open })
                        NotificationHistoryReveal(open, viewportHeight,
                            modifier = Modifier.testTag("notification_history_${group.packageName}"),
                        ) {
                            Column {
                                group.items.forEachIndexed { index, entry -> key(entry.key) {
                                    if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                    SwipeNotificationRow(entry, onDismiss) { NotificationRow(entry, foreground) }
                                } }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NotificationGroupHeader(group: NotificationAppGroup, foreground: Color, expanded: Boolean,
    onClear: () -> Unit, onToggle: () -> Unit,
) {
    val entry = group.latest
    val rotation by animateFloatAsState(if (expanded) 180f else 0f,
        tween(if (expanded) 360 else 320, easing = FastOutSlowInEasing), label = "알림 펼침 화살표")
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("notification_group_${group.packageName}")
        .semantics { stateDescription = if (expanded) "펼침" else "접힘" }.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.appName, Modifier.weight(1f), color = foreground.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.items.size}", Modifier.padding(horizontal = 6.dp), color = foreground.copy(alpha = 0.4f), fontSize = 11.sp)
            TextButton(onClear, Modifier.width(44.dp).testTag("clear_group_${group.packageName}")
                .semantics { contentDescription = "${entry.appName} 알림 모두 지우기" }, contentPadding = PaddingValues(0.dp)) {
                Text("지우기", color = foreground.copy(alpha = 0.55f), fontSize = 11.sp)
            }
            Canvas(Modifier.padding(start = 4.dp).size(16.dp).graphicsLayer { rotationZ = rotation }) {
                val path = Path().apply { moveTo(size.width * 0.2f, size.height * 0.35f); lineTo(size.width * 0.5f, size.height * 0.65f); lineTo(size.width * 0.8f, size.height * 0.35f) }
                drawPath(path, foreground.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun NotificationTime(entry: NotificationEntry, foreground: Color) {
    val context = LocalContext.current
    val time = if (android.text.format.DateUtils.isToday(entry.postedAt)) android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.postedAt))
        else android.text.format.DateFormat.format("M/d HH:mm", entry.postedAt).toString()
    Text(time, color = foreground.copy(alpha = 0.44f), fontSize = 11.sp)
}

@Composable
private fun NotificationRow(entry: NotificationEntry, foreground: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.title, Modifier.weight(1f).padding(end = 8.dp), color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            NotificationTime(entry, foreground)
        }
        if (entry.text.isNotBlank()) Text(entry.text, color = foreground.copy(alpha = if (entry.redacted) 0.5f else 0.74f),
            fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
