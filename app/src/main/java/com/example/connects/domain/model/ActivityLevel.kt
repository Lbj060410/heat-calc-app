package com.example.connects.domain.model

enum class ActivityLevel(val multiplier: Float, val label: String, val description: String) {
    SEDENTARY(1.2f, "久坐", "极少运动，大部分时间坐着（如：办公室职员）"),
    LIGHTLY_ACTIVE(1.375f, "轻度活动", "每周运动 1-3 天，或有轻体力劳动"),
    MODERATELY_ACTIVE(1.55f, "中度活动", "每周运动 3-5 天，或有中等强度劳动"),
    VERY_ACTIVE(1.725f, "高度活动", "每周运动 6-7 天，或有重体力劳动"),
    EXTRA_ACTIVE(1.9f, "极度活动", "每天专业训练，或从事极高强度劳动（如：运动员、搬运工）")
}
