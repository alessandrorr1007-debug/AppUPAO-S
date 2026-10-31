package com.example.upaos.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/** A departing screen must never pop the screen underneath it on a second tap. */
fun NavController.backFrom(source: NavBackStackEntry, onRoot: () -> Unit) {
    if (currentBackStackEntry?.id != source.id || source.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (previousBackStackEntry == null) {
        onRoot()
        return
    }
    popBackStack()
}
