package com.adhupraba.mobiledataswitcher

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.SystemServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]) // Android 14
class SimManagerTest {
    private lateinit var mockContext: Context
    private lateinit var mockSubscriptionManager: SubscriptionManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockSubscriptionManager = mockk(relaxed = true)

        every { mockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) } returns mockSubscriptionManager

        // Mock static Android logs
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getActiveSimCards returns list when permission granted`() {
        // Arrange
        val mockInfo1 = mockk<SubscriptionInfo>()
        val mockInfo2 = mockk<SubscriptionInfo>()
        every { mockSubscriptionManager.activeSubscriptionInfoList } returns listOf(mockInfo1, mockInfo2)

        // Act
        val result = SimManager.getActiveSimCards(mockContext)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.contains(mockInfo1))
    }

    @Test
    fun `getActiveSimCards returns empty list on SecurityException`() {
        // Arrange
        every { mockSubscriptionManager.activeSubscriptionInfoList } throws SecurityException("Test Exception")

        // Act
        val result = SimManager.getActiveSimCards(mockContext)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPhoneNumber returns number from SubscriptionManager on API 33+`() {
        // Arrange
        val expectedNumber = "+1234567890"
        every { mockSubscriptionManager.getPhoneNumber(1) } returns expectedNumber

        // Act
        val result = SimManager.getPhoneNumber(mockContext, 1)

        // Assert
        assertEquals(expectedNumber, result)
        verify { mockSubscriptionManager.getPhoneNumber(1) }
    }

    @Test
    fun `getPhoneNumber returns null on SecurityException`() {
        // Arrange
        every { mockSubscriptionManager.getPhoneNumber(any()) } throws SecurityException("Missing perm")

        // Act
        val result = SimManager.getPhoneNumber(mockContext, 1)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getDefaultDataSubId returns current default id`() {
        // Arrange
        mockkStatic(SubscriptionManager::class)
        every { SubscriptionManager.getDefaultDataSubscriptionId() } returns 5

        // Act
        val result = SimManager.getDefaultDataSubId()

        // Assert
        assertEquals(5, result)
    }

    @Test
    fun `switchMobileData returns false when isub is null`() {
        // Arrange
        mockkStatic(SystemServiceHelper::class)
        every { SystemServiceHelper.getSystemService("isub") } returns null

        // Act
        val result = SimManager.switchMobileData(1)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `switchMobileData handles exceptions gracefully and returns false`() {
        // Arrange
        mockkStatic(SystemServiceHelper::class)
        every { SystemServiceHelper.getSystemService("isub") } throws RuntimeException("Binder crashed")

        // Act
        val result = SimManager.switchMobileData(1)

        // Assert
        assertFalse(result)
    }
}
