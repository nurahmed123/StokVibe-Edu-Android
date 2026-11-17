package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StokVibe EDU", appName)
    }

    @Test
    fun `repository migrates legacy and root urls to institute stokvibe login`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("stockvibe_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("default_view_url", "https://institute.stokvibe.com/login").commit()

        val repo = StockVibeRepository(context)
        assertEquals("https://institute.stokvibe.com/login", repo.getStartingUrl())

        prefs.edit().putString("default_view_url", "https://institute.stokvibe.com/").commit()
        val repoFromRoot = StockVibeRepository(context)
        assertEquals("https://institute.stokvibe.com/login", repoFromRoot.getStartingUrl())
    }
}
