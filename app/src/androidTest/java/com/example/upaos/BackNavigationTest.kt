package com.example.upaos

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.upaos.ui.navigation.backFrom
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackNavigationTest {
    private fun navigationTest(block: (NavHostController, ComposeNavigator) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val owner = object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            val store = ViewModelStore()
            val nav = NavHostController(instrumentation.targetContext)
            val navigator = ComposeNavigator()
            nav.navigatorProvider.addNavigator(navigator)
            nav.setViewModelStore(store)
            nav.setLifecycleOwner(owner)
            owner.registry.currentState = Lifecycle.State.RESUMED
            nav.graph = nav.createGraph(startDestination = "home") {
                composable("home") {}
                composable("calculator") {}
                composable("settings") {}
            }
            navigator.onTransitionComplete(nav.currentBackStackEntry!!)
            try { block(nav, navigator) }
            finally {
                owner.registry.currentState = Lifecycle.State.DESTROYED
                store.clear()
            }
        }
    }

    @Test fun unguardedRepeatedBackReproducesEmptyDestination() = navigationTest { nav, navigator ->
        nav.navigate("calculator")
        navigator.onTransitionComplete(nav.currentBackStackEntry!!)
        assertTrue(nav.popBackStack())
        nav.popBackStack()
        assertNull(nav.currentDestination)
    }

    @Test fun repeatedBackFromDepartingScreenKeepsHomeVisible() = navigationTest { nav, navigator ->
        nav.navigate("calculator")
        val calculator = nav.currentBackStackEntry!!
        navigator.onTransitionComplete(calculator)
        repeat(10) { nav.backFrom(calculator) { fail("Must not fall back from an old screen") } }
        assertEquals("home", nav.currentDestination?.route)
        assertNotNull(nav.currentBackStackEntry)
    }

    @Test fun rootBackNeverRemovesTheLastScreen() = navigationTest { nav, _ ->
        val root = nav.currentBackStackEntry!!
        var fallback = false
        nav.backFrom(root) { fallback = true }
        assertTrue(fallback)
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test fun backDuringEntryTransitionIsIgnoredUntilScreenIsResumed() = navigationTest { nav, navigator ->
        nav.navigate("settings")
        val settings = nav.currentBackStackEntry!!
        assertNotEquals(Lifecycle.State.RESUMED, settings.lifecycle.currentState)
        nav.backFrom(settings) { fail("Transition is not a root navigation event") }
        assertEquals("settings", nav.currentDestination?.route)
        navigator.onTransitionComplete(settings)
        nav.backFrom(settings) { fail("Home still exists") }
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test fun staleBackCallbackCannotCloseANewerScreen() = navigationTest { nav, navigator ->
        nav.navigate("calculator")
        val calculator = nav.currentBackStackEntry!!
        navigator.onTransitionComplete(calculator)
        nav.navigate("settings")
        navigator.onTransitionComplete(nav.currentBackStackEntry!!)
        nav.backFrom(calculator) { fail("Old callback must be ignored") }
        assertEquals("settings", nav.currentDestination?.route)
    }
}
