package com.ramka.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ramka.app.ui.chat.ChatScreen
import com.ramka.app.ui.contacts.ContactsScreen
import com.ramka.app.ui.qr.QrScreen
import com.ramka.app.ui.relayguide.RelayGuideScreen
import com.ramka.app.ui.settings.SettingsScreen

object Routes {
    const val CONTACTS = "contacts"
    const val CHAT = "chat/{contactId}"
    const val QR = "qr"
    const val SETTINGS = "settings"
    const val RELAY_GUIDE = "relay_guide"

    fun chat(contactId: String) = "chat/$contactId"
}

@Composable
fun RamkaNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CONTACTS) {
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
                onAddContact = { navController.navigate(Routes.QR) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
            ChatScreen(contactId = contactId, onBack = { navController.popBackStack() })
        }
        composable(Routes.QR) {
            QrScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRelayGuide = { navController.navigate(Routes.RELAY_GUIDE) }
            )
        }
        composable(Routes.RELAY_GUIDE) {
            RelayGuideScreen(onBack = { navController.popBackStack() })
        }
    }
}
