package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.model.ApiResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.plugins.PluginManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EdgeFunctionClientTest {

    private lateinit var supabaseClientProvider: SupabaseClientProvider
    private lateinit var edgeFunctionClient: EdgeFunctionClient
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        supabaseClientProvider = mockk()
        supabaseClient = mockk()
        pluginManager = mockk()
        every { supabaseClientProvider.client } returns supabaseClient
        every { supabaseClient.pluginManager } returns pluginManager
        edgeFunctionClient = EdgeFunctionClient(supabaseClientProvider)
    }

    @Test
    fun `invoke returns NetworkError when functions plugin throws`() = runTest {
        every { pluginManager.getPlugin(Functions) } throws RuntimeException("Plugin not installed")

        val result = edgeFunctionClient.invoke("test-function")

        assertTrue(result is ApiResult.NetworkError)
    }

    @Test
    fun `invoke returns NetworkError for IO exceptions`() = runTest {
        val functions = mockk<Functions>()
        every { pluginManager.getPlugin(Functions) } returns functions
        coEvery { functions.invoke(any<String>(), any(), any()) } throws
            java.io.IOException("Connection refused")

        val result = edgeFunctionClient.invoke("test-function")

        assertTrue(result is ApiResult.NetworkError)
    }
}
