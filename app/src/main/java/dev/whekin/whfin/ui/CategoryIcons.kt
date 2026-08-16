package dev.whekin.whfin.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Маппинг строкового имени иконки категории (в БД) на Material-иконку.
 *
 * Набор намеренно outlined: залитые цветные фигуры были самым громким элементом ленты и читались
 * как стандартный Material поверх собственного визуального языка. Контурные иконки живут в одной
 * логике с hairline-линейками Quiet Ledger и оставляют цвет категории тихим акцентом.
 * Имена в БД не меняются, поэтому переключение набора не требует миграции.
 */
object CategoryIcons {

    private val byName: Map<String, ImageVector> = mapOf(
        "ShoppingCart" to Icons.Outlined.ShoppingCart,
        "Restaurant" to Icons.Outlined.Restaurant,
        "DeliveryDining" to Icons.Outlined.DeliveryDining,
        "Home" to Icons.Outlined.Home,
        "Bolt" to Icons.Outlined.Bolt,
        "DirectionsBus" to Icons.Outlined.DirectionsBus,
        "Subscriptions" to Icons.Outlined.Subscriptions,
        "PedalBike" to Icons.Outlined.PedalBike,
        "Terrain" to Icons.Outlined.Terrain,
        "LocalShipping" to Icons.Outlined.LocalShipping,
        "MedicalServices" to Icons.Outlined.MedicalServices,
        "Devices" to Icons.Outlined.Devices,
        "Chair" to Icons.Outlined.Chair,
        "AccountBalance" to Icons.Outlined.AccountBalance,
        "Savings" to Icons.Outlined.Savings,
        "Category" to Icons.Outlined.Category,
        "Payments" to Icons.Outlined.Payments,
        "Work" to Icons.Outlined.Work,
        "Sell" to Icons.Outlined.Sell,
        "Percent" to Icons.Outlined.Percent,
        "VolunteerActivism" to Icons.Outlined.VolunteerActivism,
        "Favorite" to Icons.Outlined.FavoriteBorder,
        "CardGiftcard" to Icons.Outlined.CardGiftcard,
        "HealthAndSafety" to Icons.Outlined.HealthAndSafety,
        "Backpack" to Icons.Outlined.Backpack,
        "AcUnit" to Icons.Outlined.AcUnit,
        "Luggage" to Icons.Outlined.Luggage,
        "ReceiptLong" to Icons.Outlined.ReceiptLong,
        "Handyman" to Icons.Outlined.Handyman,
        "Key" to Icons.Outlined.Key,
        "ContentCut" to Icons.Outlined.ContentCut,
        "Celebration" to Icons.Outlined.Celebration,
        "ShoppingBag" to Icons.Outlined.ShoppingBag,
        "Gavel" to Icons.Outlined.Gavel,
        "Router" to Icons.Outlined.Router,
        "Vaccines" to Icons.Outlined.Vaccines,
        "FitnessCenter" to Icons.Outlined.FitnessCenter,
        "MusicNote" to Icons.Outlined.MusicNote,
        "Diversity3" to Icons.Outlined.Diversity3,
        "HelpOutline" to Icons.AutoMirrored.Outlined.Help,
    )

    fun resolve(name: String?, isTransfer: Boolean = false): ImageVector = when {
        isTransfer -> Icons.Outlined.SwapHoriz
        name != null -> byName[name] ?: Icons.Outlined.Category
        else -> Icons.Outlined.Category
    }
}
