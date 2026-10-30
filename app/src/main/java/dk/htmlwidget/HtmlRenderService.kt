package dk.htmlwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews

class HtmlRenderService : Service() {

    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        main.post { makeHtmlWidget() }

        return START_NOT_STICKY
    }

    private fun makeHtmlWidget() {

        val web = WebView(this)

        web.settings.javaScriptEnabled = true
        web.setBackgroundColor(
            android.graphics.Color.TRANSPARENT
        )

        val width = 1000
        val height = 500

        web.webViewClient = object : WebViewClient() {

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

                    web.layout(
                        0,
                        0,
                        width,
                        height
                    )

                    val bitmap = Bitmap.createBitmap(
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

                        val remoteViews = RemoteViews(
                            packageName,
                            R.layout.widget_layout
                        )

                        remoteViews.setImageViewBitmap(
                            R.id.widget_image,
                            bitmap
                        )

                        manager.updateAppWidget(
                            id,
                            remoteViews
                        )
                    }

                    web.destroy()
                    stopSelf()

                }, 1000)
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport"
                      content="width=device-width, initial-scale=1">
                <style>
                    html, body {
                        margin: 0;
                        width: 100%;
                        height: 100%;
                        background: #171717;
                        color: white;
                        font-family: sans-serif;
                    }

                    .box {
                        box-sizing: border-box;
                        width: 100%;
                        height: 100%;
                        padding: 55px;
                    }

                    h1 {
                        font-size: 64px;
                        margin: 0 0 25px 0;
                    }

                    p {
                        font-size: 38px;
                        margin: 0;
                    }
                </style>
            </head>

            <body>
                <div class="box">
                    <h1>HTML VIRKER!</h1>
                    <p>Denne tekst kommer fra en rigtig HTML-side.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        web.loadDataWithBaseURL(
            null,
            html,
            "text/html",
            "UTF-8",
            null
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
