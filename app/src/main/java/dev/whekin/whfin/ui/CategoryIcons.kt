package dev.whekin.whfin.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.ui.graphics.vector.ImageVector

/** Маппинг строкового имени иконки категории (в БД) на Material-иконку. */
object CategoryIcons {

    private val byName: Map<String, ImageVector> = mapOf(
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "Restaurant" to Icons.Default.Restaurant,
        "DeliveryDining" to Icons.Default.DeliveryDining,
        "Home" to Icons.Default.Home,
        "Bolt" to Icons.Default.Bolt,
        "DirectionsBus" to Icons.Default.DirectionsBus,
        "Subscriptions" to Icons.Default.Subscriptions,
        "PedalBike" to Icons.Default.PedalBike,
        "Terrain" to Icons.Default.Terrain,
        "LocalShipping" to Icons.Default.LocalShipping,
        "MedicalServices" to Icons.Default.MedicalServices,
        "Devices" to Icons.Default.Devices,
        "Chair" to Icons.Default.Chair,
        "AccountBalance" to Icons.Default.AccountBalance,
        "Savings" to Icons.Default.Savings,
        "Category" to Icons.Default.Category,
        "Payments" to Icons.Default.Payments,
        "Work" to Icons.Default.Work,
        "Sell" to Icons.Default.Sell,
        "Percent" to Icons.Default.Percent,
        "VolunteerActivism" to Icons.Default.VolunteerActivism,
        "Favorite" to Icons.Default.Favorite,
        "CardGiftcard" to Icons.Default.CardGiftcard,
        "HelpOutline" to Icons.AutoMirrored.Filled.Help,
    )

    fun resolve(name: String?, isTransfer: Boolean = false): ImageVector = when {
        isTransfer -> Icons.Default.SwapHoriz
        name != null -> byName[name] ?: Icons.Default.Category
        else -> Icons.Default.Category
    }
}
