package com.adhupraba.mobiledataswitcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhupraba.mobiledataswitcher.ui.theme.MobileDataSwitcherTheme
import rikka.shizuku.Shizuku
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Will be updated via launched effect
    }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        // Handle result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SimManager.init()

        Shizuku.addRequestPermissionResultListener(shizukuListener)

        setContent {
            MobileDataSwitcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        activity = this
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }

    fun requestPhoneState() {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        ))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, activity: MainActivity) {
    var hasPhoneState by remember { 
        mutableStateOf(
            activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    var shizukuActive by remember { mutableStateOf(Shizuku.pingBinder()) }
    var shizukuGranted by remember { 
        mutableStateOf(shizukuActive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) 
    }
    
    var simList by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var defaultSubId by remember { mutableStateOf(-1) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPhoneState = activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                                activity.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                try {
                    shizukuActive = Shizuku.pingBinder()
                    if (shizukuActive) {
                        shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }
                } catch (e: Exception) {
                    shizukuActive = false
                    shizukuGranted = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuActive = true
            shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuActive = false
            shizukuGranted = false
        }
        
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }

    LaunchedEffect(hasPhoneState, shizukuActive, shizukuGranted) {
        if (hasPhoneState) {
            simList = SimManager.getActiveSimCards(activity)
            defaultSubId = SimManager.getDefaultDataSubId()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mobile Data Switcher",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            if (!hasPhoneState) {
                Button(onClick = { activity.requestPhoneState() }) {
                    Text("Grant Phone Permission")
                }
            } else if (!shizukuActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Shizuku is not running", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Please start Shizuku first.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            } else if (!shizukuGranted) {
                Button(onClick = { Shizuku.requestPermission(100) }) {
                    Text("Grant Shizuku Permission")
                }
            } else if (simList.isEmpty()) {
                Text("No SIM cards detected.", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (hasPhoneState && shizukuGranted && simList.isNotEmpty()) {
            itemsIndexed(simList) { index, sim ->
                val isDefault = sim.subscriptionId == defaultSubId
                SimCardView(
                    simName = sim.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}",
                    phoneNumber = SimManager.getPhoneNumber(activity, sim.subscriptionId),
                    isDefault = isDefault,
                    onClick = {
                        if (!isDefault) {
                            SimManager.switchMobileData(sim.subscriptionId)
                            defaultSubId = sim.subscriptionId
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SimCardView(simName: String, phoneNumber: String?, isDefault: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = simName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (!phoneNumber.isNullOrBlank()) {
                    Text(
                        text = phoneNumber,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            if (isDefault) {
                Text(
                    text = "Active Data",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}