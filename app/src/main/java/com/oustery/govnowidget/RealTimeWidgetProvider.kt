package com.oustery.govnowidget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.oustery.govnowidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.*

class RealTimeWidgetProvider : AppWidgetProvider() {

    private val previousChanges = mutableMapOf<Int, Double>()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Schedule periodic updates
        scheduleNextUpdate(context)
    }

    private fun scheduleNextUpdate(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val intent = Intent(context, RealTimeWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, RealTimeWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }, 30000) // 30 seconds
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Fetch data from the API and update the widget
        fetchData(context, views, appWidgetManager, appWidgetId)

        // Intent to update widget on click
        val intent = Intent(context, RealTimeWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent)
    }

    @SuppressLint("DefaultLocale")
    private fun fetchData(context: Context, views: RemoteViews, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.geckoterminal.com/api/v2/networks/ton/pools/EQAf2LUJZMdxSAGhlp-A60AN9bqZeVM994vCOXH05JFo-7dc")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(data)
                    val price = jsonObject.getJSONObject("data").getJSONObject("attributes")
                        .getDouble("base_token_price_usd")
                    val change = jsonObject.getJSONObject("data").getJSONObject("attributes")
                        .getJSONObject("price_change_percentage").getDouble("h24")

                    val formattedPrice = "%.2f".format(price)
                    val formattedChange = "%.2f%%".format(change)

                    // Определяем цвет на основе знака изменения
                    val textColor = if (change >= 0) Color.GREEN else Color.RED

                    Handler(Looper.getMainLooper()).post {
                        views.setTextViewText(R.id.widgetTextView, formattedPrice)
                        views.setTextViewText(R.id.widgetPercentTextView, formattedChange)
                        views.setTextColor(R.id.widgetPercentTextView, textColor)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } else {
                    throw IOException("HTTP error code: ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    views.setTextViewText(R.id.widgetTextView, "Error fetching data")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            ids?.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}