package me.rerere.ai.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart

/**
 * Markmap消息项组件
 *
 * 用于显示Markmap思维导图并提供导出功能
 */
@Composable
fun MarkmapMessageItem(
    markmap: UIMessagePart.Markmap,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportInProgress by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "思维导图",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 使用AndroidView来显示WebView
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                factory = { context ->
                    MarkmapView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // 设置Markdown内容
                        setMarkdown(markmap.content)
                    }
                }
            )
            
            // 导出按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            exportInProgress = true
                            try {
                                // 找到MarkmapView并导出
                                // 注意：这种方式存在一些挑战，可能需要进一步完善
                                // 后期可以考虑使用ViewModel或其他方式来管理
                                val rootView = context.getContentView()
                                rootView?.findMarkmapViewAndExport(markmap.content)
                            } finally {
                                exportInProgress = false
                            }
                        }
                    },
                    enabled = !exportInProgress
                ) {
                    Text(if (exportInProgress) "导出中..." else "导出图片")
                }
            }
        }
    }
}

// 扩展函数：获取当前活动的根视图
private fun Context.getContentView(): ViewGroup? {
    return try {
        val field = this.javaClass.getDeclaredField("mDecor")
        field.isAccessible = true
        field.get(this) as? ViewGroup
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 扩展函数：递归查找MarkmapView并导出
private suspend fun ViewGroup.findMarkmapViewAndExport(markdownContent: String) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        
        when (child) {
            is MarkmapView -> {
                // 找到了MarkmapView，设置内容并导出
                child.setMarkdown(markdownContent)
                child.exportAsImage()
                return
            }
            is ViewGroup -> {
                // 递归查找
                child.findMarkmapViewAndExport(markdownContent)
            }
        }
    }
} 