package com.example.connects.domain.model

/** 活动水平面向的人群，用于把「健身」和「大众」两类用户的选项区分开。 */
enum class ActivityAudience(val label: String, val hint: String) {
    FITNESS("健身人群", "按每周规律力量训练的次数选，默认受众"),
    GENERAL("大众 / 非健身", "按日常生活和运动的整体活动量选")
}

/**
 * 每日总消耗的估算系数。
 *
 * 分成两套：健身人群按「每周力量训练次数」选，大众按「整体活动量」选。
 * 健身人群即使训练天数相同，也往往有更多肌肉量、训练量更大，代谢高于同活动量的普通人，
 * 所以单独给一档而不是复用大众的系数。
 */
enum class ActivityLevel(
    val multiplier: Float,
    val label: String,
    val description: String,
    val audience: ActivityAudience
) {
    // 健身人群（默认受众）
    GYM_BEGINNER(1.5f, "健身入门", "每周规律力量训练 2-3 次，其余时间以久坐为主", ActivityAudience.FITNESS),
    GYM_INTERMEDIATE(1.65f, "健身进阶", "每周力量训练 4-5 次，训练强度中等", ActivityAudience.FITNESS),
    GYM_ADVANCED(1.8f, "健身达人", "每周力量训练 6 次以上，或训练量很大", ActivityAudience.FITNESS),

    // 大众（非健身人群，按整体活动量）
    SEDENTARY(1.2f, "久坐", "几乎不运动，大部分时间坐着（如：办公室职员）", ActivityAudience.GENERAL),
    LIGHTLY_ACTIVE(1.375f, "轻度活动", "每周运动 1-3 天，或有轻体力劳动", ActivityAudience.GENERAL),
    MODERATELY_ACTIVE(1.55f, "中度活动", "每周运动 3-5 天，或有中等强度劳动", ActivityAudience.GENERAL),
    VERY_ACTIVE(1.725f, "高度活动", "每周运动 6-7 天，或有重体力劳动", ActivityAudience.GENERAL),
    EXTRA_ACTIVE(1.9f, "极度活动", "每天专业训练，或从事极高强度劳动（如：运动员、搬运工）", ActivityAudience.GENERAL);

    /** 保存到 DataStore 用的字符串，务必用这个而不是自己拼 name。 */
    val storageKey: String get() = name.lowercase()

    companion object {
        /** 受众默认是健身人群，解析不出来时按入门健身水平兜底。 */
        val DEFAULT = GYM_BEGINNER

        /**
         * 把存储里的字符串还原成枚举。
         *
         * 历史上有过几套不同的写法（`light` / `moderate` / `active` 是旧下拉框里的，
         * `lightly_active` 之类是引导页存的），这里一并兼容。以前直接
         * `ActivityLevel.valueOf(str.uppercase())`，解析不了就静默退回久坐，
         * 于是选「中度活动」的用户拿到的热量目标是按久坐算的。
         */
        fun fromStorage(raw: String?): ActivityLevel {
            val key = raw.orEmpty().trim().lowercase().replace('-', '_').replace(' ', '_')
            return entries.firstOrNull { it.storageKey == key }
                ?: when (key) {
                    "light" -> LIGHTLY_ACTIVE
                    "moderate" -> MODERATELY_ACTIVE
                    "active" -> VERY_ACTIVE
                    else -> DEFAULT
                }
        }

        /** 某一受众下的全部选项，顺序就是枚举声明顺序。 */
        fun forAudience(audience: ActivityAudience): List<ActivityLevel> =
            entries.filter { it.audience == audience }
    }
}