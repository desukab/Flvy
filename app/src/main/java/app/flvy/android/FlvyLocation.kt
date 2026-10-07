package app.flvy.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale

object FlvyLocation {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun request(activity: Activity) {
        activity.requestPermissions(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE
        )
    }

    fun resolve(context: Context, onPlace: (String) -> Unit) {
        if (!hasPermission(context)) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        val cached = providers.asSequence().mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }

        cached?.let { publish(context, it, onPlace) }

        try {
            val provider = providers.firstOrNull { lm.isProviderEnabled(it) } ?: return
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    publish(context, location, onPlace)
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                }
            }
            lm.requestLocationUpdates(provider, 0L, 0f, listener)
        } catch (_: Exception) {}
    }

    private fun publish(context: Context, location: Location, onPlace: (String) -> Unit) {
        Thread {
            val address: Address? = try {
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
            } catch (_: Exception) { null }

            val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Current location"
            val cc = address?.countryCode?.uppercase(Locale.US)
            val tz = java.util.TimeZone.getDefault().id

            val json = JSONObject().apply {
                put("city", city)
                put("lat", location.latitude)
                put("lon", location.longitude)
                if (!cc.isNullOrBlank()) put("cc", cc)
                put("tz", tz)
            }.toString()

            (context as? Activity)?.runOnUiThread { onPlace(json) }
        }.start()
    }

    const val REQUEST_CODE = 5101
}
