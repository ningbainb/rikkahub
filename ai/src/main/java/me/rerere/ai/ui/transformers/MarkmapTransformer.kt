package me.rerere.ai.ui.transformers

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.MessageTransformer
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Markmap识别转换器
 * 
 * 用于识别消息中的Markmap格式文本，并将其转换为特定的Markmap消息部分
 */
object MarkmapTransformer : MessageTransformer {
    // Markmap格式的起始和结束标记
    private const val MARKMAP_START_MARKER = "```markmap"
    private const val MARKMAP_END_MARKER = "```"
    
    override fun transform(context: Context, messages: List<UIMessage>, model: Model): List<UIMessage> {
        return messages.map { message ->
            val newParts = mutableListOf<UIMessagePart>()
            var inMarkmapBlock = false
            var markmapContent = StringBuilder()
            
            // 遍历所有文本部分，查找并提取Markmap内容
            for (part in message.parts) {
                when (part) {
                    is UIMessagePart.Text -> {
                        val lines = part.text.lines()
                        val processedText = StringBuilder()
                        
                        for (line in lines) {
                            when {
                                // 找到Markmap开始标记
                                line.trim() == MARKMAP_START_MARKER -> {
                                    inMarkmapBlock = true
                                }
                                // 找到Markmap结束标记且当前在Markmap块内
                                line.trim() == MARKMAP_END_MARKER && inMarkmapBlock -> {
                                    inMarkmapBlock = false
                                    // 添加Markmap部分
                                    if (markmapContent.isNotEmpty()) {
                                        newParts.add(UIMessagePart.Markmap(markmapContent.toString().trim()))
                                        markmapContent = StringBuilder()
                                    }
                                }
                                // 在Markmap块内，收集内容
                                inMarkmapBlock -> {
                                    markmapContent.append(line).append("\n")
                                }
                                // 普通文本
                                else -> {
                                    processedText.append(line).append("\n")
                                }
                            }
                        }
                        
                        // 添加处理后的文本（如果有）
                        val finalText = processedText.toString().trim()
                        if (finalText.isNotEmpty()) {
                            newParts.add(UIMessagePart.Text(finalText))
                        }
                    }
                    // 保留其他类型的消息部分
                    else -> newParts.add(part)
                }
            }
            
            // 如果最后还有未处理的Markmap内容，也添加进去
            if (inMarkmapBlock && markmapContent.isNotEmpty()) {
                newParts.add(UIMessagePart.Markmap(markmapContent.toString().trim()))
            }
            
            message.copy(parts = newParts)
        }
    }
} 