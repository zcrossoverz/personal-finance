package dev.personal.ledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import dev.personal.ledger.data.AccountType

/** One icon family (Material Symbols Rounded), addressed by stable string keys stored in the database. */
object LedgerIcons {
    private val map: Map<String, ImageVector> = mapOf(
        "restaurant" to Icons.Rounded.Restaurant, "lunch" to Icons.Rounded.LunchDining, "dinner" to Icons.Rounded.DinnerDining,
        "coffee" to Icons.Rounded.LocalCafe, "grocery" to Icons.Rounded.ShoppingBasket, "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation, "taxi" to Icons.Rounded.LocalTaxi, "parking" to Icons.Rounded.LocalParking,
        "shopping" to Icons.Rounded.ShoppingBag, "cart" to Icons.Rounded.ShoppingCart, "clothes" to Icons.Rounded.Checkroom,
        "household" to Icons.Rounded.Chair, "devices" to Icons.Rounded.Headphones, "movie" to Icons.Rounded.Movie,
        "health" to Icons.Rounded.HealthAndSafety, "flight" to Icons.Rounded.Flight, "gift" to Icons.Rounded.CardGiftcard,
        "home" to Icons.Rounded.Home, "receipt" to Icons.AutoMirrored.Rounded.ReceiptLong, "bolt" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop, "wifi" to Icons.Rounded.Wifi, "phone" to Icons.Rounded.PhoneAndroid,
        "subscriptions" to Icons.Rounded.Subscriptions, "installment" to Icons.Rounded.CreditScore, "more" to Icons.Rounded.MoreHoriz,
        "salary" to Icons.Rounded.Work, "bonus" to Icons.Rounded.EmojiEvents, "income" to Icons.Rounded.Payments,
        "tv" to Icons.Rounded.Tv, "music" to Icons.Rounded.MusicNote, "sparkle" to Icons.Rounded.AutoAwesome,
        "cloud" to Icons.Rounded.Cloud, "play" to Icons.Rounded.PlayCircle, "gym" to Icons.Rounded.FitnessCenter,
        "savings" to Icons.Rounded.Savings, "laptop" to Icons.Rounded.Laptop, "smartphone" to Icons.Rounded.Smartphone,
        "credit_card" to Icons.Rounded.CreditCard, "cash" to Icons.Rounded.Payments, "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.AccountBalanceWallet, "transfer" to Icons.Rounded.SwapHoriz, "family" to Icons.Rounded.FamilyRestroom, "refund" to Icons.AutoMirrored.Rounded.Undo,
    )

    val pickable: List<String> = listOf(
        "restaurant", "lunch", "dinner", "coffee", "grocery", "car", "fuel", "taxi", "parking", "shopping", "cart", "clothes",
        "household", "devices", "movie", "health", "flight", "gift", "home", "receipt", "bolt", "water", "wifi", "phone",
        "subscriptions", "installment", "family", "tv", "music", "sparkle", "cloud", "play", "gym", "savings", "laptop", "smartphone", "more",
    )

    fun of(key: String?): ImageVector = map[key] ?: Icons.Rounded.MoreHoriz

    fun forAccount(t: AccountType): ImageVector = when (t) {
        AccountType.CASH -> Icons.Rounded.Payments
        AccountType.BANK -> Icons.Rounded.AccountBalance
        AccountType.EWALLET -> Icons.Rounded.AccountBalanceWallet
        AccountType.SAVINGS -> Icons.Rounded.Savings
        AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        AccountType.OTHER -> Icons.Rounded.AccountBalanceWallet
    }
}
