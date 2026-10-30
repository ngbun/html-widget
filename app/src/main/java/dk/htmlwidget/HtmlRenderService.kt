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

        val web = WebView(this)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true

        web.setBackgroundColor(
            android.graphics.Color.WHITE
        )

        web.layout(0, 0, 900, 560)

        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                if (adBlock) {

                    val host =
                        request?.url?.host?.lowercase()
                            ?: return null

                    val blocked = listOf(
                        "doubleclick.net",
                        "googlesyndication.com",
                        "googleadservices.com",
                        "adservice.google.com",
                        "googletagmanager.com"
                    )

                    if (
                        blocked.any {
                            host == it ||
                            host.endsWith(".$it")
                        }
                    ) {

                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            204,
                            "Blocked",
                            emptyMap(),
                            ByteArrayInputStream(
                                ByteArray(0)
                            )
                        )
                    }
                }

                return super.shouldInterceptRequest(
                    view,
                    request
                )
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {

                main.postDelayed({

                    try {

                        val width = 900
                        val height = 560

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

                        web.layout(
                            0,
                            0,
                            width,
                            height
                        )

                        val bitmap =
                            Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888
                            )

                        val canvas = Canvas(bitmap)

                        web.draw(canvas)

                        val manager =
                            AppWidgetManager.getInstance(
                                this@HtmlRenderService
                            )

                        val ids =
                            manager.getAppWidgetIds(
                                ComponentName(
                                    this@HtmlRenderService,
                                    HtmlWidgetProvider::class.java
                                )
                            )

                        ids.forEach { id ->

                            val views =
                                RemoteViews(
                                    packageName,
                                    R.layout.widget_layout
                                )

                            views.setImageViewBitmap(
                                R.id.widget_image,
                                bitmap
                            )

                            manager.updateAppWidget(
                                id,
                                views
                            )
                        }

                    } catch (_: Exception) {
                    }

                    web.destroy()
                    stopSelf()

                }, 1800)
            }
        }

        when {

            !local.isNullOrBlank() -> {

                try {

                    val uri = Uri.parse(local)

                    val html =
                        contentResolver
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

                        showError(
                            web,
                            "Kunne ikke læse HTML-filen."
                        )
                    }

                } catch (_: Exception) {

                    showError(
                        web,
                        "Kunne ikke åbne HTML-filen."
                    )
                }
            }

            address.startsWith("https://") ||
            address.startsWith("http://") -> {

                web.loadUrl(address)
            }

            else -> {

                showError(
                    web,
                    "Åbn HTML Widget-appen og vælg en HTML-fil eller hjemmeside."
                )
            }
        }
    }

    private fun showError(
        web: WebView,
        message: String
    ) {

        web.loadData(
            """
            <html>
            <body style="
                font-family:sans-serif;
                padding:30px;
                background:white;
                color:black;
            ">
            <h2>HTML Widget</h2>
            <p>$message</p>
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
                Intent(
                    context,
                    HtmlRenderService::class.java
                )
            )
        }
    }
}
