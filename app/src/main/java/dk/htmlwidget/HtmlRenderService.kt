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
        handler.post { startRender() }
        return START_NOT_STICKY
    }

    private fun startRender() {

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
        } catch (_: Exception) {
            null
        }

        if (html.isNullOrBlank()) {
            stopSelf()
            return
        }

        /*
         * TRIN 1
         * Måle-WebView.
         */
        val measureWeb = WebView(this)

        measureWeb.setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        measureWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
        }

        /*
         * Start med en almindelig viewport.
         * HTML'en får selv lov til at bygge tabellen.
         */
        val measureWidth = 1000
        val measureHeight = 1000

        measureWeb.measure(
            View.MeasureSpec.makeMeasureSpec(
                measureWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                measureHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        measureWeb.layout(
            0,
            0,
            measureWidth,
            measureHeight
        )

        measureWeb.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Vent på elpriserne og tabellen.
                     */
                    handler.postDelayed({

                        /*
                         * Find det bredeste element.
                         *
                         * scrollWidth er vigtigt for tabeller
                         * og andre elementer, der går ud over
                         * den synlige viewport.
                         */
                        measureWeb.evaluateJavascript(
                            """
                            (function() {

                                var all =
                                    document.querySelectorAll('*');

                                var widest = 0;

                                for (
                                    var i = 0;
                                    i < all.length;
                                    i++
                                ) {

                                    var el = all[i];

                                    var style =
                                        window.getComputedStyle(el);

                                    if (
                                        style.display === 'none' ||
                                        style.visibility === 'hidden'
                                    ) {
                                        continue;
                                    }

                                    widest = Math.max(
                                        widest,
                                        el.scrollWidth || 0,
                                        el.offsetWidth || 0
                                    );
                                }

                                widest = Math.max(
                                    widest,
                                    document.body.scrollWidth,
                                    document.documentElement.scrollWidth
                                );

                                return Math.ceil(widest);

                            })();
                            """.trimIndent()
                        ) { result ->

                            val naturalWidth =
                                result
                                    .replace("\"", "")
                                    .trim()
                                    .toIntOrNull()
                                    ?: 1000

                            /*
                             * Lidt ekstra plads til sidste
                             * kolonne og afrunding.
                             */
                            val renderWidth =
                                (naturalWidth + 40)
                                    .coerceIn(
                                        400,
                                        6000
                                    )

                            measureWeb.destroy()

                            /*
                             * TRIN 2
                             * Ny WebView i den målte bredde.
                             */
                            renderFullHtml(
                                html,
                                localUri,
                                renderWidth
                            )
                        }

                    }, 4000)
                }
            }

        measureWeb.loadDataWithBaseURL(
            localUri,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun renderFullHtml(
        html: String,
        localUri: String,
        renderWidth: Int
    ) {

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
            )
                .roundToInt()
                .coerceAtLeast(1)

        val widgetHeight =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            )
                .roundToInt()
                .coerceAtLeast(1)

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

        /*
         * Stor nok højde til at siden kan bygges.
         * Den rigtige højde måles bagefter.
         */
        val workHeight = 1800

        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                renderWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                workHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            renderWidth,
            workHeight
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    handler.postDelayed({

                        /*
                         * Nu måler vi kun højden.
                         * Bredden ændrer vi ikke længere.
                         */
                        web.evaluateJavascript(
                            """
                            (function() {

                                return Math.ceil(
                                    Math.max(
                                        document.body.scrollHeight,
                                        document.body.offsetHeight,
                                        document.documentElement.scrollHeight,
                                        document.documentElement.offsetHeight
                                    )
                                );

                            })();
                            """.trimIndent()
                        ) { result ->

                            val contentHeight =
                                result
                                    .replace("\"", "")
                                    .trim()
                                    .toIntOrNull()
                                    ?.coerceIn(200, workHeight)
                                    ?: workHeight

                            makeBitmap(
                                web,
                                manager,
                                ids,
                                renderWidth,
                                contentHeight,
                                widgetWidth,
                                widgetHeight
                            )
                        }

                    }, 4000)
                }
            }

        web.loadDataWithBaseURL(
            localUri,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun makeBitmap(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        contentWidth: Int,
        contentHeight: Int,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

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
         * Én proportional skala.
         * Intet bliver strukket eller beskåret.
         */
        val scale =
            min(
                widgetWidth.toFloat() /
                    contentWidth.toFloat(),

                widgetHeight.toFloat() /
                    contentHeight.toFloat()
            ).coerceAtMost(1f)

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
