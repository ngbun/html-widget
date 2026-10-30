package dk.htmlwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import java.io.ByteArrayInputStream

class HtmlRenderService : Service() {

    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        main.post { render() }
        return START_NOT_STICKY
    }

    private fun render() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val local = prefs.getString("localUri", null)
        val address = prefs.getString("url", "") ?: ""
        val adBlock = prefs.getBoolean("adblock", true)

        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            ComponentName(this, HtmlWidgetProvider::class.java)
        )

        if (ids.isEmpty()) {
            stopSelf()
            return
        }

        val options = manager.getAppWidgetOptions(ids[0])
        val density = resources.displayMetrics.density

        val width = (
            options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                300
            ) * density * 1.5f
        ).toInt().coerceIn(600, 1800)

        val height = (
            options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                180
            ) * density * 1.5f
        ).toInt().coerceIn(300, 1200)

        val web = WebView(this)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        web.setBackgroundColor(
            android.graphics.Color.TRANSPARENT
        )

        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                if (!adBlock) return null

                val host = request?.url?.host?.lowercase()
                    ?: return null

                val blocked = listOf(
                    "doubleclick.net",
                    "googlesyndication.com",
                    "googleadservices.com",
                    "google-analytics.com",
                    "adnxs.com"
                )

                if (blocked.any {
                    host == it || host.endsWith(".$it")
                }) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                return null
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                main.postDelayed({

                    web.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(
                            width,
                            android.view.View.MeasureSpec.EXACTLY
                        ),
                        android.view.View.MeasureSpec.makeMeasureSpec(
                            height,
                            android.view.View.MeasureSpec.EXACTLY
                        )
                    )

                    web.layout(0, 0, width, height)

                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )

                    web.draw(Canvas(bitmap))

                    ids.forEach { id ->
                        val views = RemoteViews(
                            packageName,
                            R.layout.widget_layout
                        )

                        views.setImageViewBitmap(
                            R.id.widget_image,
                            bitmap
                        )

                        manager.updateAppWidget(id, views)
                    }

                    web.destroy()
                    stopSelf()

                }, 3500)
            }
        }

        if (!local.isNullOrBlank()) {

            try {
                val uri = Uri.parse(local)

                val html = contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (html != null) {
                    web.loadDataWithBaseURL(
                        local,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    showError(web)
                }

            } catch (_: Exception) {
                showError(web)
            }

        } else if (
            address.startsWith("https://") ||
            address.startsWith("http://")
        ) {

            web.loadUrl(address)

        } else {

            showError(web)
        }
    }

    private fun showError(web: WebView) {
        web.loadData(
            """
            <html>
            <body style="background:#111;color:white;font-family:sans-serif">
            <h2>HTML Widget</h2>
            <p>Vælg en HTML-fil eller hjemmeside i appen.</p>
            </body>
            </html>
            """.trimIndent(),
            "text/html",
            "UTF-8"
        )
    }

    companion object {
        fun start(context: Context) {
            context.startService(
                Intent(context, HtmlRenderService::class.java)
            )
        }
    }
}
