package me.rerere.ai.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MarkmapView用于渲染Markmap格式的思维导图并提供导出图片功能
 */
class MarkmapView(context: Context) : WebView(context) {
    private val markdownContent = CompletableDeferred<String>()
    private val exportImageDeferred = CompletableDeferred<String>()

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        
        // 添加JavaScript接口用于与WebView通信
        addJavascriptInterface(MarkmapJsInterface(), "AndroidInterface")
        
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 当页面加载完成后，加载Markdown内容
                loadMarkdownContent()
            }
        }
        
        // 加载Markmap基本HTML
        loadMarkmapHtml()
    }
    
    private fun loadMarkmapHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Markmap渲染</title>
                <style>
                    body, html { 
                        margin: 0; 
                        padding: 0; 
                        height: 100%; 
                        width: 100%; 
                        overflow: hidden;
                        background-color: white;
                    }
                    svg {
                        width: 100%;
                        height: 100%;
                    }
                    .markmap-container {
                        height: 100%;
                        width: 100%;
                    }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
                <script src="https://cdn.jsdelivr.net/npm/markmap-view@0.14.4"></script>
            </head>
            <body>
                <div class="markmap-container">
                    <svg id="markmap"></svg>
                </div>
                
                <script>
                    // 等待Markmap内容加载
                    let markmapObject = null;
                    
                    function renderMarkmap(markdown) {
                        const { Markmap, loadCSS, loadJS } = window.markmap;
                        
                        // 清除现有内容
                        const svg = document.getElementById('markmap');
                        svg.innerHTML = '';
                        
                        // 渲染新的Markmap
                        markmapObject = Markmap.create(svg, undefined, markdown);
                        
                        // 自动调整大小和位置
                        setTimeout(() => {
                            if (markmapObject) {
                                const { minX, maxX, minY, maxY } = markmapObject.state;
                                const width = maxX - minX + 400;
                                const height = maxY - minY + 200;
                                
                                svg.setAttribute('width', width);
                                svg.setAttribute('height', height);
                                
                                // 通知Android思维导图已渲染完成
                                AndroidInterface.onMarkmapRendered();
                            }
                        }, 500);
                    }
                    
                    // 导出图片
                    function exportAsImage() {
                        try {
                            const svg = document.getElementById('markmap');
                            
                            // 创建canvas并设置大小
                            const canvas = document.createElement('canvas');
                            const bbox = svg.getBBox();
                            
                            // 设置合适的尺寸
                            const width = Math.max(bbox.width + 100, window.innerWidth);
                            const height = Math.max(bbox.height + 100, window.innerHeight);
                            
                            canvas.width = width;
                            canvas.height = height;
                            
                            // 准备SVG数据
                            const data = new XMLSerializer().serializeToString(svg);
                            const svgBlob = new Blob([data], {type: 'image/svg+xml;charset=utf-8'});
                            const url = URL.createObjectURL(svgBlob);
                            
                            // 绘制到Canvas
                            const ctx = canvas.getContext('2d');
                            ctx.fillStyle = 'white';
                            ctx.fillRect(0, 0, canvas.width, canvas.height);
                            
                            const img = new Image();
                            img.onload = function() {
                                ctx.drawImage(img, 0, 0);
                                
                                // 导出为base64
                                const imgBase64 = canvas.toDataURL('image/png');
                                AndroidInterface.onImageExported(imgBase64);
                                
                                // 清理URL对象
                                URL.revokeObjectURL(url);
                            };
                            
                            img.src = url;
                        } catch (e) {
                            AndroidInterface.onExportError("导出失败: " + e.message);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
    }
    
    private fun loadMarkdownContent() {
        // 等待Markdown内容准备好
        Thread {
            try {
                val markdown = markdownContent.await()
                
                // 执行JavaScript渲染Markmap
                post {
                    evaluateJavascript("renderMarkmap(`$markdown`)", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    /**
     * 设置Markdown内容
     */
    fun setMarkdown(content: String) {
        markdownContent.complete(content)
    }
    
    /**
     * 导出思维导图为图片
     */
    suspend fun exportAsImage(): String = withContext(Dispatchers.IO) {
        // 重置导出Deferred
        exportImageDeferred.complete("")
        val newDeferred = CompletableDeferred<String>()
        
        withContext(Dispatchers.Main) {
            // 调用JavaScript执行导出操作
            evaluateJavascript("exportAsImage()", null)
        }
        
        // 等待JavaScript回调完成
        newDeferred.await()
    }
    
    /**
     * JavaScript接口，用于WebView和Android之间的通信
     */
    inner class MarkmapJsInterface {
        /**
         * 当思维导图渲染完成时调用
         */
        @JavascriptInterface
        fun onMarkmapRendered() {
            // 可以在这里做一些UI通知
        }
        
        /**
         * 当图片导出完成时调用
         */
        @JavascriptInterface
        fun onImageExported(base64Image: String) {
            try {
                // 保存图片到文件
                val imageFile = saveBase64ImageToFile(base64Image)
                exportImageDeferred.complete(imageFile.absolutePath)
            } catch (e: Exception) {
                exportImageDeferred.completeExceptionally(e)
            }
        }
        
        /**
         * 导出出错时调用
         */
        @JavascriptInterface
        fun onExportError(errorMsg: String) {
            exportImageDeferred.completeExceptionally(Exception(errorMsg))
        }
    }
    
    /**
     * 将Base64图片保存到文件
     */
    private fun saveBase64ImageToFile(base64Image: String): File {
        // 移除base64前缀
        val base64Data = if (base64Image.contains(",")) {
            base64Image.split(",")[1]
        } else {
            base64Image
        }
        
        // 解码base64
        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        
        // 创建文件
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File(storageDir, "Markmap_$timeStamp.png")
        
        // 写入文件
        FileOutputStream(imageFile).use { it.write(imageBytes) }
        
        // 通知用户
        Toast.makeText(context, "思维导图已保存到: ${imageFile.absolutePath}", Toast.LENGTH_LONG).show()
        
        return imageFile
    }
} 