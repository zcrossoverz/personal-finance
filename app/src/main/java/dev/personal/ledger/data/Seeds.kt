package dev.personal.ledger.data

/** Default categories and quick presets. IDs are fixed so presets can reference categories before insert. */
object Seeds {
    // Category ids
    const val FOOD = 1L; const val LUNCH = 2L; const val DINNER = 3L; const val CAFE = 4L; const val GROCERY = 5L
    const val TRANSPORT = 10L; const val FUEL = 11L; const val TAXI = 12L; const val PARKING = 13L
    const val SHOPPING = 20L; const val ONLINE = 21L; const val CLOTHES = 22L; const val HOUSEHOLD = 23L; const val ELECTRONICS = 24L
    const val FUN = 30L; const val HEALTH = 40L; const val TRAVEL = 50L; const val GIFTS = 60L; const val FAMILY = 61L
    const val HOUSING = 70L; const val RENT = 71L
    const val BILLS = 80L; const val POWER = 81L; const val WATER = 82L; const val INTERNET = 83L; const val PHONE = 84L
    const val SUBS = 90L; const val INSTALLMENTS = 95L; const val OTHER = 99L
    const val SALARY = 100L; const val BONUS = 101L; const val OTHER_INCOME = 102L

    private fun c(id: Long, name: String, icon: String, color: Int, parent: Long? = null, nature: CategoryNature = CategoryNature.FLEXIBLE, aliases: String = "", income: Boolean = false, order: Int = id.toInt()) =
        Category(id, name, parent, icon, color, income, nature, aliases, false, order)

    val categories = listOf(
        c(FOOD, "Ăn uống", "restaurant", 0, aliases = "food,an,eat,anuong"),
        c(LUNCH, "Ăn trưa", "lunch", 0, FOOD, aliases = "lunch,trua"),
        c(DINNER, "Ăn tối", "dinner", 0, FOOD, aliases = "dinner,toi"),
        c(CAFE, "Cafe", "coffee", 1, FOOD, aliases = "cafe,coffee,cf,trasua"),
        c(GROCERY, "Đi chợ", "grocery", 0, FOOD, aliases = "groceries,cho,sieuthi,market"),
        c(TRANSPORT, "Di chuyển", "car", 2, aliases = "transport,dichuyen"),
        c(FUEL, "Xăng xe", "fuel", 2, TRANSPORT, aliases = "fuel,gas,xang"),
        c(TAXI, "Grab", "taxi", 2, TRANSPORT, aliases = "grab,taxi,be,xanhsm"),
        c(PARKING, "Gửi xe", "parking", 2, TRANSPORT, aliases = "parking,guixe"),
        c(SHOPPING, "Mua sắm", "shopping", 3, aliases = "shopping,muasam"),
        c(ONLINE, "Shopee", "cart", 3, SHOPPING, aliases = "shopee,lazada,tiki,online"),
        c(CLOTHES, "Quần áo", "clothes", 3, SHOPPING, aliases = "clothes,quanao"),
        c(HOUSEHOLD, "Gia dụng", "household", 3, SHOPPING, aliases = "household,giadung"),
        c(ELECTRONICS, "Điện tử", "devices", 3, SHOPPING, aliases = "electronics,dientu,tech"),
        c(FUN, "Giải trí", "movie", 4, aliases = "fun,entertainment,giaitri,movie,phim"),
        c(HEALTH, "Sức khỏe", "health", 5, aliases = "health,thuoc,suckhoe"),
        c(TRAVEL, "Du lịch", "flight", 6, aliases = "travel,dulich"),
        c(GIFTS, "Quà tặng", "gift", 4, aliases = "gift,qua"),
        c(FAMILY, "Gia đình", "family", 4, nature = CategoryNature.FIXED, aliases = "family,giadinh,bome"),
        c(HOUSING, "Nhà ở", "home", 7, nature = CategoryNature.FIXED, aliases = "housing,nha"),
        c(RENT, "Tiền trọ", "home", 7, HOUSING, CategoryNature.FIXED, aliases = "rent,tro,tientro"),
        c(BILLS, "Hóa đơn", "receipt", 7, nature = CategoryNature.FIXED, aliases = "bills,hoadon"),
        c(POWER, "Điện", "bolt", 7, BILLS, CategoryNature.FIXED, aliases = "electricity,dien"),
        c(WATER, "Nước", "water", 7, BILLS, CategoryNature.FIXED, aliases = "water,nuoc"),
        c(INTERNET, "Internet", "wifi", 7, BILLS, CategoryNature.FIXED, aliases = "internet,wifi,mang"),
        c(PHONE, "Điện thoại", "phone", 7, BILLS, CategoryNature.FIXED, aliases = "phone,dt,4g"),
        c(SUBS, "Subscription", "subscriptions", 6, nature = CategoryNature.FIXED, aliases = "subscription,sub"),
        c(INSTALLMENTS, "Trả góp", "installment", 5, nature = CategoryNature.FIXED, aliases = "installment,tragop"),
        c(OTHER, "Khác", "more", 1, aliases = "other,khac"),
        c(SALARY, "Lương", "salary", 0, income = true, aliases = "salary,luong"),
        c(BONUS, "Thưởng", "bonus", 0, income = true, aliases = "bonus,thuong"),
        c(OTHER_INCOME, "Thu nhập khác", "income", 0, income = true, aliases = "income,thunhap"),
    )

    /** Default quick presets. [pinned] ones keep their order on Home. */
    fun presets(): List<Preset> = listOf(
        Preset(1, "Ăn uống", "restaurant", 0, TxType.EXPENSE, FOOD, seedAmounts = "35000,50000,65000,100000", contextual = true, pinned = true, sortOrder = 0),
        Preset(2, "Cafe", "coffee", 1, TxType.EXPENSE, CAFE, seedAmounts = "29000,35000,45000,55000", pinned = true, sortOrder = 1),
        Preset(3, "Di chuyển", "taxi", 2, TxType.EXPENSE, TAXI, seedAmounts = "25000,45000,65000,90000", pinned = true, sortOrder = 2),
        Preset(4, "Xăng xe", "fuel", 2, TxType.EXPENSE, FUEL, seedAmounts = "50000,70000,100000", pinned = true, sortOrder = 3),
        Preset(5, "Shopee", "cart", 3, TxType.EXPENSE, ONLINE, seedAmounts = "99000,199000,350000", sortOrder = 4),
        Preset(6, "Shopping", "shopping", 3, TxType.EXPENSE, SHOPPING, seedAmounts = "200000,500000", sortOrder = 5),
        Preset(7, "Đi chợ", "grocery", 0, TxType.EXPENSE, GROCERY, seedAmounts = "150000,250000,400000", sortOrder = 6),
        Preset(8, "Tiền trọ", "home", 7, TxType.EXPENSE, RENT, sortOrder = 7),
        Preset(9, "Điện", "bolt", 7, TxType.EXPENSE, POWER, sortOrder = 8),
        Preset(10, "Nước", "water", 7, TxType.EXPENSE, WATER, sortOrder = 9),
        Preset(11, "Internet", "wifi", 7, TxType.EXPENSE, INTERNET, sortOrder = 10),
        Preset(12, "Subscription", "subscriptions", 6, TxType.EXPENSE, SUBS, sortOrder = 11),
        Preset(13, "Hóa đơn", "receipt", 7, TxType.EXPENSE, BILLS, sortOrder = 12),
        Preset(14, "Giải trí", "movie", 4, TxType.EXPENSE, FUN, seedAmounts = "100000,200000", sortOrder = 13),
        Preset(15, "Khác", "more", 1, TxType.EXPENSE, OTHER, sortOrder = 14),
    )
}
