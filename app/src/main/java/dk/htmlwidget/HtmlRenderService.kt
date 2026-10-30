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

        /*
         * Den RIGTIGE størrelse på widgetten.
         */
        val widgetWidth =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    300
                ) * density
            )
                .roundToInt()
                .coerceAtLeast(300)

        val widgetHeight =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            )
                .roundToInt()
                .coerceAtLeast(200)

        val web = WebView(this)

        /*
         * Denne beholder vi.
         * Uden software-rendering fik vi tidligere
         * kun en tom/sort widget.
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

            builtInZoomControls = false
            displayZoomControls = false
        }

        /*
         * WebView'en ændres IKKE senere.
         * Den får widgettens størrelse fra starten.
         */
        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                widgetWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                widgetHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            widgetWidth,
            widgetHeight
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Din elpris-HTML henter priser med
                     * JavaScript, så vi giver den tid
                     * til at bygge hele tabellen først.
                     */
                    handler.postDelayed({

                        fitHtml(web)

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

    private fun fitHtml(
        web: WebView
    ) {

        /*
         * JavaScript måler hele dokumentets naturlige
         * bredde og højde.
         *
         * Derefter beregnes én zoom-faktor, så både
         * bredde OG højde kan være i viewporten.
         *
         * Smalle HTML-sider bliver ikke gjort mindre
         * end nødvendigt.
         */
        web.evaluateJavascript(
            """
            (function() {

                document.body.style.zoom = '1';

                var doc =
                    document.documentElement;

                var body =
                    document.body;

                var viewportWidth =
                    doc.clientWidth;

                var viewportHeight =
                    doc.clientHeight;

                var contentWidth =
                    Math.max(
                        body.scrollWidth,
                        body.offsetWidth,
                        doc.scrollWidth,
                        doc.offsetWidth
                    );

                var contentHeight =
                    Math.max(
                        body.scrollHeight,
                        body.offsetHeight,
                        doc.scrollHeight,
                        doc.offsetHeight
                    );

                var scaleX =
                    viewportWidth / contentWidth;

                var scaleY =
                    viewportHeight / contentHeight;

                var scale =
                    Math.min(
                        1,
                        scaleX,
                        scaleY
                    );

                /*
                 * En lille margin så sidste kolonne
                 * ikke rammer helt ud i kanten.
                 */
                scale = scale * 0.97;

                document.body.style.transformOrigin =
                    'top left';

                document.body.style.zoom =
                    scale.toString();

                document.documentElement.style.overflow =
                    'hidden';

                document.body.style.overflow =
                    'visible';

                return scale.toString();

            })();
            """.trimIndent()
        ) {

            /*
             * Giv Chromium tid til at tegne siden
             * igen efter zoom.
             */
            handler.postDelayed({

                makeBitmap(web)

            }, 1500)
        }
    }

    private fun makeBitmap(
        web: WebView
    ) {

        /*
         * Tving endnu en draw/layout-cyklus.
         */
        web.invalidate()

        handler.postDelayed({

            val width =
                web.width.coerceAtLeast(1)

            val height =
                web.height.coerceAtLeast(1)

            val bitmap =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(bitmap)

            web.draw(canvas)

            showBitmap(bitmap)

            web.destroy()
            stopSelf()

        }, 500)
    }

    private fun showBitmap(
        bitmap: Bitmap
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
