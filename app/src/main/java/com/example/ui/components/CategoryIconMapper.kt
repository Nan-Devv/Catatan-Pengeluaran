package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Store
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconMapper {

    val availableIcons: List<Pair<String, ImageVector>> = listOf(
        "restaurant" to Icons.Rounded.Restaurant,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "receipt_long" to Icons.Rounded.ReceiptLong,
        "medical_services" to Icons.Rounded.MedicalServices,
        "school" to Icons.Rounded.School,
        "movie" to Icons.Rounded.Movie,
        "family_restroom" to Icons.Rounded.FamilyRestroom,
        "home" to Icons.Rounded.Home,
        "payments" to Icons.Rounded.Payments,
        "store" to Icons.Rounded.Store,
        "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
        "call_received" to Icons.AutoMirrored.Rounded.CallReceived,
        "add_card" to Icons.Rounded.AddCard,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "account_balance" to Icons.Rounded.AccountBalance,
        "smartphone" to Icons.Rounded.Smartphone,
        "credit_card" to Icons.Rounded.CreditCard,
        "savings" to Icons.Rounded.Savings,
        "fitness" to Icons.Rounded.FitnessCenter,
        "flight" to Icons.Rounded.Flight,
        "gift" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "money" to Icons.Rounded.AttachMoney,
        "more_horiz" to Icons.Rounded.MoreHoriz
    )

    fun iconForKey(key: String?): ImageVector {
        return availableIcons.firstOrNull { it.first == key }?.second ?: Icons.Rounded.MoreHoriz
    }
}
