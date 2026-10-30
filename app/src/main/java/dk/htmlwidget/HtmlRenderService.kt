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

        /*
         * De to mulige kilder.
         */
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
            )
                .roundToInt()
                .coerceAtLeast(1)

        val height =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            )
                .roundToInt()
                .coerceAtLeast(1)

        val web = WebView(this)

        /*
         * DENNE BEHOLDER VI.
         * Det var software-renderingen,
         * der fik widgetten til at virke.
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

        /*
         * WebView får præcis widgettens størrelse.
         */
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

                    /*
                     * Vent på JavaScript, billeder,
                     * priser osv.
                     */
                    handler.postDelayed({

                        /*
                         * Hvis det er vores HTML-widget,
                         * får fitGrid mulighed for at
                         * tilpasse tabellen igen.
                         *
                         * På almindelige hjemmesider
                         * gør resize-eventet ingen skade.
                         */
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
         * KILDE 1:
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
         * KILDE 2:
         * Hjemmeside.
         */
        if (
            address.startsWith("https://") ||
            address.startsWith("http://")
        ) {

            web.loadUrl(address)

            return
        }

        /*
         * Ingen gyldig kilde.
         */
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
                <p>
                    Vælg en HTML-fil eller hjemmeside
                    i appen.
                </p>
            </body>
            </html>
            """.trimIndent(),
            "text/html",
            "UTF-8"
        )
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
