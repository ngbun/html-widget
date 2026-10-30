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
import kotlin.math.min
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
            loadWithOverviewMode = false
        }

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Først giver vi siden tid til at hente
                     * priser og bygge tabellen.
                     */
                    handler.postDelayed({

                        web.evaluateJavascript(
                            """
                            (function() {
                                var body = document.body;
                                var html = document.documentElement;

                                var w = Math.max(
                                    body.scrollWidth,
                                    body.offsetWidth,
                                    html.scrollWidth,
                                    html.offsetWidth,
                                    html.clientWidth
                                );

                                var h = Math.max(
                                    body.scrollHeight,
                                    body.offsetHeight,
                                    html.scrollHeight,
                                    html.offsetHeight,
                                    html.clientHeight
                                );

                                return w + "," + h;
                            })();
                            """.trimIndent()
                        ) { result ->

                            /*
                             * Resultatet kommer eksempelvis som:
                             * "2450,720"
                             */
                            val clean =
                                result
                                    .replace("\"", "")
                                    .trim()

                            val parts =
                                clean.split(",")

                            var contentWidth =
                                parts
                                    .getOrNull(0)
                                    ?.toIntOrNull()
                                    ?: 1600

                            var contentHeight =
                                parts
                                    .getOrNull(1)
                                    ?.toIntOrNull()
                                    ?: 900

                            /*
                             * Lidt sikkerhedsmargin, så den
                             * sidste time ikke bliver klippet.
                             */
                            contentWidth += 40
                            contentHeight += 20

                            /*
                             * Beskyt telefonen mod ekstremt
                             * store bitmaps.
                             */
                            contentWidth =
                                contentWidth.coerceIn(
                                    600,
                                    5000
                                )

                            contentHeight =
                                contentHeight.coerceIn(
                                    300,
                                    2000
                                )

                            renderFullPage(
                                web,
                                manager,
                                ids,
                                contentWidth,
                                contentHeight,
                                widgetWidth,
                                widgetHeight
                            )
                        }

                    }, 3500)
                }
            }

        /*
         * Start bredt nok til at HTML-tabellen kan
         * bygge alle 24 timer.
         */
        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                4000,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                1000,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            4000,
            1000
        )

        web.loadDataWithBaseURL(
            localUri,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun renderFullPage(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        contentWidth: Int,
        contentHeight: Int,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                contentWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                contentHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            contentWidth,
            contentHeight
        )

        web.invalidate()

        handler.postDelayed({

            val fullBitmap =
                Bitmap.createBitmap(
                    contentWidth,
                    contentHeight,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(fullBitmap)

            web.draw(canvas)

            /*
             * Fit HELE billedet ind i widgetten.
             */
            val scaleX =
                widgetWidth.toFloat() /
                    contentWidth.toFloat()

            val scaleY =
                widgetHeight.toFloat() /
                    contentHeight.toFloat()

            val scale =
                min(scaleX, scaleY)

            val finalWidth =
                (contentWidth * scale)
                    .roundToInt()
                    .coerceAtLeast(1)

            val finalHeight =
                (contentHeight * scale)
                    .roundToInt()
                    .coerceAtLeast(1)

            val fittedBitmap =
                Bitmap.createScaledBitmap(
                    fullBitmap,
                    finalWidth,
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
