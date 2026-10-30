package dk.htmlwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
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
        main.post { renderWidgets() }
        return START_NOT_STICKY
    }

    private fun renderWidgets() {
        val manager = AppWidgetManager.getInstance(this)

        val ids = manager.getAppWidgetIds(
            ComponentName(this, HtmlWidgetProvider::class.java)
        )

        if (ids.isEmpty()) {
            stopSelf()
            return
        }

        renderNext(manager, ids, 0)
    }

    private fun renderNext(
        manager: AppWidgetManager,
        ids: IntArray,
        index: Int
    ) {
        if (index >= ids.size) {
            stopSelf()
            return
        }

        val widgetId = ids[index]
        val options = manager.getAppWidgetOptions(widgetId)

        val minWidthDp = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            300
        )

        val minHeightDp = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            180
        )

        val density = resources.displayMetrics.density

        val widgetWidth =
            (minWidthDp * density).toInt().coerceAtLeast(400)

        val widgetHeight =
            (minHeightDp * density).toInt().coerceAtLeast(220)

        /*
         * Vi renderer i højere opløsning end selve widgetten.
         * Det giver skarpere tekst, når Android viser billedet.
         */
        val renderWidth =
            (widgetWidth * 1.5f).toInt().coerceAtMost(1800)

        val renderHeight =
            (widgetHeight * 1.5f).toInt().coerceAtMost(1200)

        renderWidget(
            manager,
            widgetId,
            renderWidth,
            renderHeight
        ) {
            renderNext(manager, ids, index + 1)
        }
    }

    private fun renderWidget(
        manager: AppWidgetManager,
        widgetId: Int,
        width: Int,
        height: Int,
        finished: () -> Unit
    ) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val local = prefs.getString("localUri", null)
        val address = prefs.getString("url", "") ?: ""
        val adBlock = prefs.getBoolean("adblock", true)

        val web = WebView(this)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }

        web.setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
                        "googletagmanager.com",
                        "google-analytics.com",
                        "adnxs.com",
                        "criteo.com"
                    )

                    if (
                        blocked.any {
                            host == it || host.endsWith(".$it")
                        }
                    ) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            204,
                            "Blocked",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                /*
                 * Giver JavaScript/API-kald i HTML-filen lidt tid til
                 * at hente priser og tegne tabellen færdig.
                 */
                main.postDelayed({

                    try {
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

                        val canvas = Canvas(bitmap)
                        web.draw(canvas)

                        val views = RemoteViews(
                            packageName,
                            R.layout.widget_layout
                        )

                        views.setImageViewBitmap(
                            R.id.widget_image,
                            bitmap
                        )

                        manager.updateAppWidget(
                            widgetId,
                            views
                        )

                    } catch (_: Exception) {
                    } finally {
                        web.destroy()
                        finished()
                    }

                }, 3500)
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
                           
