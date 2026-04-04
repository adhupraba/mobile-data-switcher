package com.adhupraba.mobiledataswitcher

import android.content.Context
import android.os.IBinder
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object SimManager {
    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (e: Exception) {
                 Log.e("SimManager", "Failed to bypass hidden APIs", e)
            }
        }
    }

    fun getActiveSimCards(context: Context): List<SubscriptionInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        return try {
            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("SimManager", "Missing READ_PHONE_STATE permission", e)
            emptyList()
        }
    }

    fun getPhoneNumber(context: Context, subId: Int): String? {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                subscriptionManager.getPhoneNumber(subId).takeIf { it.isNotBlank() }
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.getActiveSubscriptionInfo(subId)?.number?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getDefaultDataSubId(): Int {
        return SubscriptionManager.getDefaultDataSubscriptionId()
    }

    fun switchMobileData(subId: Int): Boolean {
        try {
            val iSubBinder = SystemServiceHelper.getSystemService("isub")
            if (iSubBinder != null) {
                val shizukuBinder = ShizukuBinderWrapper(iSubBinder)
                val stubClass = Class.forName("com.android.internal.telephony.ISub\$Stub")
                val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
                val iSubInstance = asInterfaceMethod.invoke(null, shizukuBinder)
                
                val setDefaultDataSubIdMethod = iSubInstance.javaClass.getMethod("setDefaultDataSubId", Int::class.javaPrimitiveType)
                setDefaultDataSubIdMethod.invoke(iSubInstance, subId)
                
                // Allow a tiny delay for the OS to finalize the SIM switch
                Thread.sleep(100)
                
                // Use a direct Shizuku shell process to enable data.
                // Because we just changed the default data SIM above, 'svc data enable' will turn it on for the new SIM.
                try {
                    val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    newProcessMethod.isAccessible = true
                    
                    val process = newProcessMethod.invoke(
                        null,
                        arrayOf("sh", "-c", "svc data enable"),
                        null,
                        null
                    ) as Process
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e("SimManager", "Failed to run svc data enable with reflection", e)
                }
                
                return true
            } else {
                Log.e("SimManager", "isub service is null")
            }
        } catch (e: Exception) {
            Log.e("SimManager", "Failed to switch mobile data", e)
        }
        return false
    }
}
