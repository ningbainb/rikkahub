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
import kotlinx.coroutines.*
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
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        
        addJavascriptInterface(MarkmapJsInterface(), "AndroidInterface")
        
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadMarkdownContent()
            }
        }
        
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
                    let markmapObject = null;
                    
                    function renderMarkmap(markdown) {
                        const { Markmap, loadCSS, loadJS } = window.markmap;
                        const svg = document.getElementById('markmap');
                        svg.innerHTML = '';
                        markmapObject = Markmap.create(svg, undefined, markdown);
                        setTimeout(() => {
                            if (markmapObject) {
                                const { minX, maxX, minY, maxY } = markmapObject.state;
                                const width = maxX - minX + 400;
                                const height = maxY - minY + 200;
                                svg.setAttribute('width', width);
                                svg.setAttribute('height', height);
                                AndroidInterface.onMarkmapRendered();
                            }
                        }, 500);
                    }
                    
                    function exportAsImage() {
                        try {
                            const svg = document.getElementById('markmap');
                            const canvas = document.createElement('canvas');
                            const bbox = svg.getBBox();
                            const width = Math.max(bbox.width + 100, window.innerWidth);
                            const height = Math.max(bbox.height + 100, window.innerHeight);
                            canvas.width = width;
                            canvas.height = height;
                            const data = new XMLSerializer().serializeToString(svg);
                            const svgBlob = new Blob([data], {type: 'image/svg+xml;charset=utf-8'});
                            const url = URL.createObjectURL(svgBlob);
                            const ctx = canvas.getContext('2d');
                            ctx.fillStyle = 'white';
                            ctx.fillRect(0, 0, canvas.width, canvas.height);
                            const img = new Image();
                            img.onload = function() {
                                ctx.drawImage(img, 0, 0);
                                const imgBase64 = canvas.toDataURL('image/png');
                                AndroidInterface.onImageExported(imgBase64);
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
        viewScope.launch {
            try {
                val markdown = withContext(Dispatchers.IO) {
                    markdownContent.await()
                }
                
                withContext(Dispatchers.Main) {
                    evaluateJavascript("renderMarkmap(`$markdown`)", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load Markmap: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 设置Markdown内容
     */
    fun setMarkdown(content: String) {
        if (!markdownContent.isCompleted) {
            markdownContent.complete(content)
        } else {
            println("MarkmapView: Markdown content already set or completed.")
        }
    }
    
    /**
     * 导出思维导图为图片
     */
    suspend fun exportAsImage(): String {
        val currentDeferred = CompletableDeferred<String>()

        withContext(Dispatchers.Main) {
            evaluateJavascript("exportAsImage()", null)
        }
        
        if(exportImageDeferred.isCompleted) {
        }
        withContext(Dispatchers.Main) {
           evaluateJavascript("exportAsImage()", null)
        }
        return ""
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope.cancel()
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
            viewScope.launch(Dispatchers.Main) {
            }
        }
        
        /**
         * 当图片导出完成时调用
         */
        @JavascriptInterface
        fun onImageExported(base64Image: String) {
           viewScope.launch(Dispatchers.IO) {
                try {
                    val imageFile = saveBase64ImageToFile(base64Image)
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
           }
        }
        
        /**
         * 导出出错时调用
         */
        @JavascriptInterface
        fun onExportError(errorMsg: String) {
             viewScope.launch {
                 Toast.makeText(context, "Export error: $errorMsg", Toast.LENGTH_LONG).show()
             }
        }
    }
    
    @Throws(IOException::class)
    private fun saveBase64ImageToFile(base64Image: String): File {
        val base64Data = base64Image.substringAfter("base64,", base64Image)
        
        val imageBytes: ByteArray = try {
             Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid Base64 string", e)
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(storageDir, "RikkaHubMarkmaps")
        if (!appDir.exists()) {
            if (!appDir.mkdirs()) {
                 throw IOException("Could not create directory: ${appDir.absolutePath}")
            }
        }
        val imageFile = File(appDir, "Markmap_$timeStamp.png")
        
        try {
            FileOutputStream(imageFile).use { it.write(imageBytes) }
        } catch(e: Exception) {
            throw IOException("Failed to write image to file: ${imageFile.absolutePath}", e)
        }
        
        println("Markmap saved to: ${imageFile.absolutePath}")
        return imageFile
    }
} 