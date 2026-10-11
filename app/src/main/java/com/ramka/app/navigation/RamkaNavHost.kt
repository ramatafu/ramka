package com.ramka.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ramka.app.ui.call.CallScreen
import com.ramka.app.ui.call.activityCallViewModel
import com.ramka.app.ui.chat.ChatScreen
import com.ramka.app.ui.contacts.ContactsScreen
import com.ramka.app.ui.qr.QrScreen
import com.ramka.app.ui.relay.RelayEditViewModel
import com.ramka.app.ui.relay.RelayEditScreen
import com.ramka.app.ui.relay.RelayListScreen
import com.ramka.app.ui.relayguide.RelayGuideScreen
import com.ramka.app.ui.settings.SettingsScreen
import com.ramka.domain.call.CallState

object Routes {
    const val CONTACTS = "contacts"
    const val CHAT = "chat/{contactId}"
    const val QR = "qr"
    const val SETTINGS = "settings"
    const val RELAY_GUIDE = "relay_guide"
    const val RELAY_LIST = "relay_list"
    const val RELAY_EDIT = "relay_edit/{entryId}"
    const val CALL = "call/{contactId}"

    fun chat(contactId: String) = "chat/$contactId"

    fun call(contactId: String) = "call/$contactId"

    /** null — добавление новой записи (в путь подставляется [RelayEditViewModel.NEW_ENTRY]). */
    fun relayEdit(entryId: String?) = "relay_edit/${entryId ?: RelayEditViewModel.NEW_ENTRY}"
}

@Composable
fun RamkaNavHost() {
    val navController = rememberNavController()

    // Входящий звонок (шаг 7.3): CallViewModel одна на Activity и слушает сигналинг всё время, пока
    // приложение на экране; при CallState.Incoming открываем экран звонка поверх текущего.
    val callViewModel = activityCallViewModel()
    val callState by callViewModel.state.collectAsState()
    LaunchedEffect(callState) {
        val incoming = callState as? CallState.Incoming ?: return@LaunchedEffect
        val onCallScreen = navController.currentBackStackEntry?.destination?.route == Routes.CALL
        if (!onCallScreen) navController.navigate(Routes.call(incoming.contactId)) { launchSingleTop = true }
    }

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
            ChatScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onCall = { navController.navigate(Routes.call(contactId)) }
            )
        }
        composable(
            route = Routes.CALL,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
            CallScreen(contactId = contactId, onClose = { navController.popBackStack() })
        }
        composable(Routes.QR) {
            QrScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRelays = { navController.navigate(Routes.RELAY_LIST) }
            )
        }
        composable(Routes.RELAY_LIST) {
            RelayListScreen(
                onBack = { navController.popBackStack() },
                onAddRelay = { navController.navigate(Routes.relayEdit(null)) },
                onEditRelay = { id -> navController.navigate(Routes.relayEdit(id)) },
                onOpenRelayGuide = { navController.navigate(Routes.RELAY_GUIDE) }
            )
        }
        composable(
            route = Routes.RELAY_EDIT,
            arguments = listOf(
                navArgument(RelayEditViewModel.ARG_ENTRY_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            RelayEditScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RELAY_GUIDE) {
            RelayGuideScreen(onBack = { navController.popBackStack() })
        }
    }
}
