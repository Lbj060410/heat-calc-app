package com.example.connects.domain.usecase

import com.example.connects.domain.model.ActivityLevel
import javax.inject.Inject

class CalculateTdeeUseCase @Inject constructor() {
    operator fun invoke(bmr: Float, activityLevel: ActivityLevel): Float {
        return bmr * activityLevel.multiplier
    }

    /** 存储里读到的是字符串时用这个重载，解析交给 [ActivityLevel.fromStorage]。 */
    operator fun invoke(bmr: Float, activityLevelStr: String?): Float {
        return bmr * ActivityLevel.fromStorage(activityLevelStr).multiplier
    }
}
