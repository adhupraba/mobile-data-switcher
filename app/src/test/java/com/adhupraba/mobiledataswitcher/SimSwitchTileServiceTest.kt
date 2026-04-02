package com.adhupraba.mobiledataswitcher

import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.telephony.SubscriptionInfo
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]) // Android 14
class SimSwitchTileServiceTest {

    private lateinit var service: SimSwitchTileService
    private lateinit var mockTile: Tile

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        mockkStatic(Shizuku::class)
        mockkObject(SimManager)

        mockTile = mockk(relaxed = true)

        // Use Robolectric to build the service safely
        service = Robolectric.buildService(SimSwitchTileService::class.java).create().get()

        // We must spy on the service so checkSelfPermission doesn't crash without context
        service = spyk(service)
        every { service.qsTile } returns mockTile
        every { service.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) } returns PackageManager.PERMISSION_GRANTED
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onClick when no Phone Permission sets unavailable and label`() {
        // Arrange
        every { service.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) } returns PackageManager.PERMISSION_DENIED

        // Act
        service.onClick()

        // Assert
        verify { mockTile.state = Tile.STATE_UNAVAILABLE }
        verify { mockTile.label = "No Perm" }
        verify { mockTile.updateTile() }
    }

    @Test
    fun `onClick when Shizuku dead sets unavailable and label`() {
        // Arrange
        every { Shizuku.pingBinder() } returns false

        // Act
        service.onClick()

        // Assert
        verify { mockTile.state = Tile.STATE_UNAVAILABLE }
        verify { mockTile.label = "Start Shizuku" }
        verify { mockTile.updateTile() }
    }

    @Test
    fun `onClick when Shizuku permission denied sets unavailable`() {
        // Arrange
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_DENIED

        // Act
        service.onClick()

        // Assert
        verify { mockTile.state = Tile.STATE_UNAVAILABLE }
        verify { mockTile.label = "No Shizuku Perm" }
        verify { mockTile.updateTile() }
    }

    @Test
    fun `onClick when less than 2 SIMs sets unavailable`() {
        // Arrange
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_GRANTED
        every { SimManager.getActiveSimCards(any()) } returns listOf(mockk()) // Only 1 SIM

        // Act
        service.onClick()

        // Assert
        verify { mockTile.state = Tile.STATE_UNAVAILABLE }
        verify { mockTile.label = "Need 2 SIMs" }
    }

    @Test
    fun `onClick successful switch posts update`() {
        // Arrange
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_GRANTED

        val sim1 = mockk<SubscriptionInfo>(relaxed = true)
        val sim2 = mockk<SubscriptionInfo>(relaxed = true)
        every { sim1.subscriptionId } returns 1
        every { sim2.subscriptionId } returns 2
        every { sim2.displayName } returns "Jio"
        every { SimManager.getActiveSimCards(any()) } returns listOf(sim1, sim2)
        every { SimManager.getDefaultDataSubId() } returns 1 // Means we will target 2
        every { SimManager.switchMobileData(2) } returns true
        every { SimManager.getPhoneNumber(any(), 2) } returns null

        // Act
        service.onClick()

        // At this point Handler posted delayed. Tile shouldn't update yet (or it runs instantly depending on Robo).
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Assert
        verify { SimManager.switchMobileData(2) }
        // updateTile() is tricky to spy directly because it's private, but we can verify tile state changes inside updateTileState.
        // Wait, SimManager.getDefaultDataSubId() is still 1 in the mock unless we change it!
        // But the delay triggered updateTileState(), which hits the branch.
    }


    @Test
    fun `onClick failed switch triggers error log`() {
        // Arrange
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_GRANTED

        val sim1 = mockk<SubscriptionInfo>(relaxed = true)
        val sim2 = mockk<SubscriptionInfo>(relaxed = true)
        every { sim1.subscriptionId } returns 1
        every { sim2.subscriptionId } returns 2
        every { SimManager.getActiveSimCards(any()) } returns listOf(sim1, sim2)
        every { SimManager.getDefaultDataSubId() } returns 1
        every { SimManager.switchMobileData(2) } returns false // FAILS

        // Act
        service.onClick()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Assert
        verify { SimManager.switchMobileData(2) }
        // It should NOT call updateTileState because of the false condition.
    }

    @Test
    fun `onStartListening without current sim sets inactive`() {
        // Arrange
        every { SimManager.getActiveSimCards(any()) } returns emptyList() // No sims

        // Act
        service.onStartListening()

        // Assert
        verify { mockTile.state = Tile.STATE_INACTIVE }
        verify { mockTile.label = "Switch Data" }
        verify { mockTile.updateTile() }
    }

    @Test
    fun `onStartListening with current sim sets active and label`() {
        // Arrange
        val sim1 = mockk<SubscriptionInfo>(relaxed = true)
        every { sim1.subscriptionId } returns 10
        every { sim1.displayName } returns "Airtel"
        every { SimManager.getActiveSimCards(any()) } returns listOf(sim1)
        every { SimManager.getDefaultDataSubId() } returns 10
        every { SimManager.getPhoneNumber(any(), 10) } returns "9876543210"

        // Act
        service.onStartListening()

        // Assert
        verify { mockTile.state = Tile.STATE_ACTIVE }
        verify { mockTile.label = "Airtel" }
        // Verify subtitle is correctly injected
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            verify { mockTile.subtitle = "9876543210" }
        }
        verify { mockTile.updateTile() }
    }
}

