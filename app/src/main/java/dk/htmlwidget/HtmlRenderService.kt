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
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews

class HtmlRenderService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        handler.post { renderHtml() }

        return START_NOT_STICKY
    }

    private fun renderHtml() {

        val width = 1000
        val height = 500

        val web = WebView(this)

        web.setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {

                handler.postDelayed({

                    web.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            width,
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(
                            height,
                            View.MeasureSpec.EXACTLY
                        )
                    )

                    web.layout(
                        0,
                        0,
                        width,
                        height
                    )

                    web.invalidate()

                    handler.postDelayed({

                        val bitmap =
                            Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888
                            )

                        val canvas = Canvas(bitmap)

                        web.draw(canvas)

                        showBitmap(bitmap)

                        web.destroy()
                        stopSelf()

                    }, 1000)

                }, 1000)
            }
        }

        web.loadDataWithBaseURL(
            "https://localhost/",
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport"
                    content="width=device-width, initial-scale=1.0">

                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background: white;
                    }

                    body {
                        font-family: sans-serif;
                        color: black;
                    }

                    .test {
                        padding: 60px;
                    }

                    h1 {
                        font-size: 70px;
                        margin: 0 0 30px 0;
                    }

                    p {
                        font-size: 40px;
                    }
                </style>
            </head>

            <body>
                <div class="test">
                    <h1>HTML VIRKER!</h1>
                    <p>WebView kan nu tegne tekst.</p>
                </div>
            </body>
            </html>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showBitmap(bitmap: Bitmap) {

        val manager =
            AppWidgetManager.getInstance(this)

        val ids =
            manager.getAppWidgetIds(
                ComponentName(
                    this,
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
