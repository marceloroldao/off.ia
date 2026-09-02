package ia.off

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class NetworkState(
    val connected: Boolean,
    val validated: Boolean,
    val wifi: Boolean,
    val metered: Boolean,
)

fun currentNetworkState(context: Context): NetworkState {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork
        ?: return NetworkState(connected = false, validated = false, wifi = false, metered = true)
    val capabilities = manager.getNetworkCapabilities(network)
        ?: return NetworkState(connected = false, validated = false, wifi = false, metered = true)

    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return NetworkState(
        connected = hasInternet,
        validated = validated,
        wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        metered = manager.isActiveNetworkMetered,
    )
}
