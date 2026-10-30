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
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import kotlin.math.roundToInt

class HtmlRenderService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        handler.post { render() }

        return START_NOT_STICKY
    }

    private fun render() {

        val prefs =
            getSharedPreferences("settings", MODE_PRIVATE)

        val localUri =
            prefs.getString("localUri", null)

        val address =
            prefs.getString("url", "") ?: ""

        val manager =
            AppWidgetManager.getInstance(this)

        val ids =
            manager.getAppWidgetIds(
                ComponentName(
                    this,
                    HtmlWidgetProvider::class.java
                )
            )

        if (ids.isEmpty()) {
            stopSelf()
            return
        }

        val options =
            manager.getAppWidgetOptions(ids[0])

        val density =
            resources.displayMetrics.density

        val width =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    300
                ) * density
            ).roundToInt()

        val height =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            ).roundToInt()

        val web = WebView(this)

        /*
         * Denne skal blive.
         * Det var den, der løste den sorte/tomme
         * WebView tidligere.
         */
        web.setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        web.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true

            useWideViewPort = true
            loadWithOverviewMode = false
        }

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

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    handler.postDelayed({

                        web.evaluateJavascript(
                            """
                            window.dispatchEvent(
                                new Event('resize')
                            );
                            """.trimIndent()
                        ) {

                            handler.postDelayed({

                                makeBitmap(
                                    web,
                                    manager,
                                    ids,
                                    width,
                                    height
                                )

                            }, 1200)
                        }

                    }, 4000)
                }
            }

        /*
         * Lokal HTML-fil.
         */
        if (!localUri.isNullOrBlank()) {

            val html = try {

                contentResolver
                    .openInputStream(
                        Uri.parse(localUri)
                    )
                    ?.bufferedReader()
                    ?.use { it.readText() }

            } catch (_: Exception) {
                null
            }

            if (!html.isNullOrBlank()) {

                web.loadDataWithBaseURL(
                    localUri,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )

                return
            }
        }

        /*
         * Hjemmeside.
         */
        if (
            address.startsWith("https://") ||
            address.startsWith("http://")
        ) {

            web.loadUrl(address)

            return
        }

        stopSelf()
    }

    private fun makeBitmap(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        width: Int,
        height: Int
    ) {

        web.invalidate()

        handler.postDelayed({

            val bitmap =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(bitmap)

            web.draw(canvas)

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

            web.destroy()
            stopSelf()

        }, 300)
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
