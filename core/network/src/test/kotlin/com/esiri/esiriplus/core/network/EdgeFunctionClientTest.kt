package com.esiri.esiriplus.core.network

import io.mockk.every
import io.mockk.mockk
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.plugins.PluginManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class EdgeFunctionClientTest {

    private lateinit var supabaseClientProvider: SupabaseClientProvider
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        supabaseClientProvider = mockk()
        supabaseClient = mockk()
        pluginManager = mockk()
        every { supabaseClientProvider.client } returns supabaseClient
        every { supabaseClient.pluginManager } returns pluginManager
    }

    @Test
    fun `provider and client are accessible`() {
        // Verify that the mock setup works — SupabaseClientProvider provides a client
        assertNotNull(supabaseClientProvider.client)
        assertNotNull(supabaseClient.pluginManager)
    }
}
