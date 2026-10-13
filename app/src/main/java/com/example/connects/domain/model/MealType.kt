package com.example.connects.domain.model

/**
 * 餐次。
 *
 * 全项目只有这一份定义。以前 HomeViewModel、HomeScreen、FoodEntryEntity 各抄了一套
 * 「早餐/午餐/晚餐/加餐」常量（HomeViewModel 里还多了一组内容完全相同的 LEGACY_ 副本），
 * 属于改一处漏一处的典型。
 *
 * 数据库里存的是 [label] 原文；[labelOf] 是写入和读取共用的归一化入口。认不出来的值
 * 一律归到加餐，这样首页的餐次统计和日志页的分组永远对得上，也不会有记录在按餐次
 * 拆分时凭空消失。
 */
enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐");

    companion object {

        /** 界面展示与日志分组的固定顺序。 */
        val ordered: List<MealType> = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

        fun fromLabel(raw: String?): MealType? {
            val key = raw.orEmpty().trim()
            return entries.firstOrNull { it.label == key }
        }

        fun labelOf(raw: String?): String = (fromLabel(raw) ?: SNACK).label

        /**
         * 按固定顺序把记录分到各餐次下，空餐次也会保留一条空列表
         * （日志页需要展示「这顿还没记」）。
         */
        fun group(entries: List<FoodEntry>): List<Pair<MealType, List<FoodEntry>>> =
            ordered.map { meal ->
                meal to entries.filter { labelOf(it.mealType) == meal.label }
            }
    }
}
