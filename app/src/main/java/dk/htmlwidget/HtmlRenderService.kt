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
        handler.post { renderHtml() }
        return START_NOT_STICKY
    }

    private fun renderHtml() {

        val prefs =
            getSharedPreferences("settings", MODE_PRIVATE)

        val localUri =
            prefs.getString("localUri", null)

        if (localUri.isNullOrBlank()) {
            stopSelf()
            return
        }

        val html = try {
            contentResolver
                .openInputStream(Uri.parse(localUri))
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

        if (html.isNullOrBlank()) {
            stopSelf()
            return
        }

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

        val widgetWidth =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    300
                ) * density
            ).roundToInt()

        val widgetHeight =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            ).roundToInt()

        /*
         * Bred viewport til hele 24-timers tabellen.
         *
         * WebView får derefter lov til selv at zoome
         * hele siden ned.
         */
        val renderWidth = 3600
        val renderHeight = 1100

        val web = WebView(this)

        web.setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true

            useWideViewPort = true
            loadWithOverviewMode = true

            builtInZoomControls = false
            displayZoomControls = false
        }

        /*
         * Gør selve HTML-dokumentet bredt.
         * Vi ændrer ikke din HTML-fil permanent.
         */
        val fittedHtml =
            html.replace(
                "<head>",
                """
                <head>
                <style>
                    html {
                        width: 2400px !important;
                    }

                    body {
                        width: 2400px !important;
                        max-width: none !important;
                        overflow: visible !important;
                    }

                    table {
                        max-width: none !important;
                    }
                </style>
                """.trimIndent(),
                ignoreCase = true
            )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Tid til at hente alle elpriserne.
                     */
                    handler.postDelayed({

                        web.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                renderWidth,
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                renderHeight,
                                View.MeasureSpec.EXACTLY
                            )
                        )

                        web.layout(
                            0,
                            0,
                            renderWidth,
                            renderHeight
                        )

                        web.invalidate()

                        handler.postDelayed({

                            val fullBitmap =
                                Bitmap.createBitmap(
                                    renderWidth,
                                    renderHeight,
                                    Bitmap.Config.ARGB_8888
                                )

                            val canvas =
                                Canvas(fullBitmap)

                            web.draw(canvas)

                            /*
                             * Nu skalerer vi hele det færdige
                             * billede til widgettens bredde.
                             */
                            val ratio =
                                widgetWidth.toFloat() /
                                    renderWidth.toFloat()

                            val finalHeight =
                                (renderHeight * ratio)
                                    .roundToInt()
                                    .coerceAtMost(widgetHeight)
                                    .coerceAtLeast(1)

                            val fittedBitmap =
                                Bitmap.createScaledBitmap(
                                    fullBitmap,
                                    widgetWidth,
                                    finalHeight,
                                    true
                                )

                            ids.forEach { id ->

                                val views =
                                    RemoteViews(
                                        packageName,
                                        R.layout.widget_layout
                                    )

                                views.setImageViewBitmap(
                                    R.id.widget_image,
                                    fittedBitmap
                                )

                                manager.updateAppWidget(
                                    id,
                                    views
                                )
                            }

                            if (fittedBitmap !== fullBitmap) {
                                fullBitmap.recycle()
                            }

                            web.destroy()
                            stopSelf()

                        }, 1500)

                    }, 3500)
                }
            }

        web.loadDataWithBaseURL(
            localUri,
            fittedHtml,
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
