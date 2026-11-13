package com.example.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class WifiDirectManager(private val context: Context) {

    companion object {
        private const val SERVICE_INSTANCE = "_minishare"
        private const val SERVICE_TYPE = "_presence._tcp"
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val systemPeers = ConcurrentHashMap<String, WifiP2pDevice>()
    private val servicePeers = ConcurrentHashMap<String, WifiP2pDevice>()
    private val servicePeerNames = ConcurrentHashMap<String, String>()
    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    @Volatile
    private var isActivelyListening = false

    init {
        initChannel()
        registerReceiver()
    }

    private fun initChannel() {
        channel = manager?.initialize(context, context.mainLooper) {
            Log.d("WifiDirectManager", "Channel disconnected")
            channel = null
        }
    }

    private fun ensureChannel(): WifiP2pManager.Channel? {
        if (channel == null) {
            initChannel()
        }
        return channel
    }

    fun ensureRegistered() {
        ensureChannel()
        if (receiver == null) {
            registerReceiver()
        }
    }

    private fun publishMergedPeers() {
        val merged = LinkedHashMap<String, WifiP2pDevice>()
        for ((addr, device) in systemPeers) {
            val friendlyName = servicePeerNames[addr]
            if (!friendlyName.isNullOrBlank() && friendlyName != device.deviceName) {
                merged[addr] = WifiP2pDevice(device).apply {
                    deviceName = friendlyName
                }
            } else {
                merged[addr] = device
            }
        }
        for ((addr, device) in servicePeers) {
            if (!merged.containsKey(addr)) {
                val friendlyName = servicePeerNames[addr]
                merged[addr] = if (!friendlyName.isNullOrBlank()) {
                    WifiP2pDevice(device).apply { deviceName = friendlyName }
                } else {
                    device
                }
            }
        }
        // Show peers advertising Mini Share first
        _peers.value = merged.values.sortedByDescending {
            servicePeerNames.containsKey(it.deviceAddress) || servicePeers.containsKey(it.deviceAddress)
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    }

                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                        _isDiscovering.value = (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val extraPeers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST, WifiP2pDeviceList::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)
                        }
                        if (extraPeers != null) {
                            systemPeers.clear()
                            extraPeers.deviceList.forEach { dev ->
                                systemPeers[dev.deviceAddress] = dev
                            }
                            publishMergedPeers()
                        }
                        requestPeers()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val p2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                        }
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (p2pInfo?.groupFormed == true && p2pInfo.groupOwnerAddress != null) {
                            _connectionInfo.value = p2pInfo
                            _isConnected.value = true
                            requestConnectionInfo()
                        } else if (p2pInfo?.groupFormed == true || networkInfo?.isConnected == true) {
                            _isConnected.value = true
                            requestConnectionInfo()
                        } else {
                            _isConnected.value = false
                            _connectionInfo.value = null
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                        } else {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        if (device != null) {
                            _thisDevice.value = device
                        }
                    }
                }
            }
        }

        // RECEIVER_EXPORTED is required for system/APEX Wi-Fi P2P broadcasts across Android 13-17
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    @SuppressLint("MissingPermission")
    fun requestThisDeviceInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ch = ensureChannel() ?: return
            manager?.requestDeviceInfo(ch) { device ->
                if (device != null) {
                    _thisDevice.value = device
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun registerLocalReceiveService(userName: String, onDone: (() -> Unit)? = null) {
        val ch = ensureChannel() ?: run {
            onDone?.invoke()
            return
        }
        val safeName = userName.trim().ifEmpty { "Receiver" }
        val record = mapOf("name" to safeName)
        val info = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_TYPE, record)
        localServiceInfo = info

        manager?.clearLocalServices(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.addLocalService(ch, info, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        onDone?.invoke()
                    }

                    override fun onFailure(reason: Int) {
                        onDone?.invoke()
                    }
                })
            }

            override fun onFailure(reason: Int) {
                manager.addLocalService(ch, info, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        onDone?.invoke()
                    }

                    override fun onFailure(reason: Int) {
                        onDone?.invoke()
                    }
                })
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startReceiveListening(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = ensureChannel() ?: run {
            onFailure?.invoke(WifiP2pManager.ERROR)
            return
        }
        requestThisDeviceInfo()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isActivelyListening) {
                _isDiscovering.value = true
                onSuccess?.invoke()
                return
            }
            manager?.startListening(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    isActivelyListening = true
                    _isDiscovering.value = true
                    onSuccess?.invoke()
                }

                override fun onFailure(reason: Int) {
                    isActivelyListening = false
                    // Fallback to discoverPeers if vendor HAL rejects startListening
                    discoverPeers(onSuccess = onSuccess, onFailure = onFailure)
                }
            })
        } else {
            discoverPeers(onSuccess = onSuccess, onFailure = onFailure)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopReceiveListening() {
        isActivelyListening = false
        val ch = channel ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager?.stopListening(ch, null)
        }
        manager?.clearLocalServices(ch, null)
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = ensureChannel() ?: run {
            onFailure?.invoke(WifiP2pManager.ERROR)
            return
        }
        _isDiscovering.value = true
        manager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                requestPeers()
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                onFailure?.invoke(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun discoverMiniShareServices() {
        val ch = ensureChannel() ?: return
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, device ->
            val name = record["name"]
            if (!name.isNullOrBlank()) {
                servicePeerNames[device.deviceAddress] = name
                servicePeers[device.deviceAddress] = device
                publishMergedPeers()
            }
        }
        val servListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, resourceType ->
            if (instanceName.equals(SERVICE_INSTANCE, ignoreCase = true)) {
                servicePeers[resourceType.deviceAddress] = resourceType
                publishMergedPeers()
            }
        }
        manager?.setDnsSdResponseListeners(ch, servListener, txtListener)

        val request = WifiP2pDnsSdServiceRequest.newInstance()
        serviceRequest = request
        manager?.clearServiceRequests(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.addServiceRequest(ch, request, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.discoverServices(ch, null)
                    }

                    override fun onFailure(reason: Int) {}
                })
            }

            override fun onFailure(reason: Int) {
                manager.addServiceRequest(ch, request, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.discoverServices(ch, null)
                    }

                    override fun onFailure(reason: Int) {}
                })
            }
        })
    }

    fun stopDiscovery() {
        val ch = channel ?: return
        manager?.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
            }
        })
        manager?.clearServiceRequests(ch, null)
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        val ch = ensureChannel() ?: return
        manager?.requestPeers(ch) { peersList: WifiP2pDeviceList? ->
            val list = peersList?.deviceList?.toList() ?: emptyList()
            systemPeers.clear()
            list.forEach { dev ->
                systemPeers[dev.deviceAddress] = dev
            }
            publishMergedPeers()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: WifiP2pDevice,
        groupOwnerIntent: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int) -> Unit)? = null
    ) {
        val ch = ensureChannel() ?: run {
            onFailure?.invoke(WifiP2pManager.ERROR)
            return
        }
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            this.groupOwnerIntent = groupOwnerIntent
        }

        manager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                onFailure?.invoke(reason)
            }
        })
    }

    fun cancelConnect(onComplete: (() -> Unit)? = null) {
        val ch = channel ?: run {
            onComplete?.invoke()
            return
        }
        manager?.cancelConnect(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onComplete?.invoke()
            }

            override fun onFailure(reason: Int) {
                onComplete?.invoke()
            }
        })
    }

    fun requestConnectionInfo(onReady: ((WifiP2pInfo) -> Unit)? = null) {
        val ch = ensureChannel() ?: return
        manager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
            if (info != null && info.groupFormed) {
                _connectionInfo.value = info
                _isConnected.value = true
                onReady?.invoke(info)
            }
        }
    }

    fun resetConnectionState() {
        isActivelyListening = false
        _isConnected.value = false
        _connectionInfo.value = null
        systemPeers.clear()
        servicePeers.clear()
        servicePeerNames.clear()
        _peers.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun prepareCleanSession(onReady: () -> Unit) {
        resetConnectionState()
        val ch = ensureChannel() ?: run {
            onReady()
            return
        }
        manager?.cancelConnect(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = clearStaleGroupIfAny(ch, onReady)
            override fun onFailure(reason: Int) = clearStaleGroupIfAny(ch, onReady)
        }) ?: onReady()
    }

    @SuppressLint("MissingPermission")
    private fun clearStaleGroupIfAny(ch: WifiP2pManager.Channel, onReady: () -> Unit) {
        manager?.requestConnectionInfo(ch) { info ->
            if (info?.groupFormed == true) {
                manager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = onReady()
                    override fun onFailure(reason: Int) = onReady()
                })
            } else {
                onReady()
            }
        } ?: onReady()
    }

    fun disconnect() {
        isActivelyListening = false
        _isConnected.value = false
        _connectionInfo.value = null
        val ch = channel ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager?.stopListening(ch, null)
        }
        manager?.clearLocalServices(ch, null)
        manager?.clearServiceRequests(ch, null)
        manager?.cancelConnect(ch, null)
        manager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isConnected.value = false
                _connectionInfo.value = null
            }

            override fun onFailure(reason: Int) {
                _isConnected.value = false
                _connectionInfo.value = null
            }
        })
    }

    fun unregister() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        receiver = null
    }
}
