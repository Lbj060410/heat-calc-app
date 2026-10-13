package com.example.connects.domain.usecase

import com.example.connects.domain.model.MacroTargets
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 按「每公斤体重」算三大营养素目标，而不是按热量百分比。
 *
 * 蛋白质需求跟体重走、不跟热量走：按热量百分比算的话，100kg 的减脂用户只能拿到
 * 约 0.9 g/kg 蛋白质，正好算反了。健身人群的推荐区间参考：
 *   蛋白质：减脂 2.0、增肌 1.8、维持 1.4 g/kg（1.6 g/kg 是增肌收益的平台期）
 *   脂肪：减脂 0.5~1.0 g/kg 且不低于总热量 20%~25%；增肌/维持约 25%~30%
 *   碳水：剩下的热量全部给碳水
 *
 * 对齐：先把蛋白质、脂肪取整，再由取整后的克数倒推碳水克数，
 * 这样三者换算回的热量与目标最多差 2 kcal（碳水是 4 kcal/g，无法做到绝对相等）。
 */
class CalculateMacroTargetsUseCase @Inject constructor() {

    operator fun invoke(weightKg: Float, goal: String, targetCalories: Int): MacroTargets {
        val weight = weightKg.coerceAtLeast(1f)
        val calories = targetCalories.coerceAtLeast(0)

        val (proteinPerKg, fatPerKg) = when (goal) {
            "lose" -> 2.0f to 0.8f
            "gain" -> 1.8f to 0.9f
            else -> 1.4f to 0.9f
        }

        // 先各算各的，再取整成「克」——后面的碳水要以取整后的值为准。
        var proteinG = (weight * proteinPerKg).roundToInt().toFloat()
        var fatG = (weight * fatPerKg).roundToInt().toFloat()

        // 脂肪先受「不超过总热量 35%」的天花板约束。
        val fatCeilingG = (calories * FAT_CALORIE_CEILING / 9f).roundToInt().toFloat()
        if (fatG > fatCeilingG) fatG = fatCeilingG

        // 蛋白质 + 脂肪不能挤占全部热量，否则碳水没有位置（低热量 + 大体重时会遇到）。
        // 超出预算时先压脂肪，仍超出再压蛋白质，各自有下限。
        val proteinFatBudget = calories * MAX_PROTEIN_FAT_SHARE
        val fatFloorG = (weight * MIN_FAT_PER_KG).roundToInt().toFloat()
        val proteinFloorG = (weight * MIN_PROTEIN_PER_KG).roundToInt().toFloat()

        if (proteinG * 4f + fatG * 9f > proteinFatBudget) {
            val fatAvailableG = ((proteinFatBudget - proteinG * 4f) / 9f).roundToInt().toFloat()
            fatG = fatAvailableG.coerceAtLeast(fatFloorG)
        }
        if (proteinG * 4f + fatG * 9f > proteinFatBudget) {
            val proteinAvailableG = ((proteinFatBudget - fatG * 9f) / 4f).roundToInt().toFloat()
            proteinG = proteinAvailableG.coerceAtLeast(proteinFloorG)
        }

        // 碳水吸收余量，使三者换算回的热量贴近目标。
        val carbsG = ((calories - proteinG * 4f - fatG * 9f) / 4f)
            .roundToInt()
            .toFloat()
            .coerceAtLeast(0f)

        return MacroTargets(proteinG = proteinG, carbsG = carbsG, fatG = fatG)
    }

    private companion object {
        /** 蛋白质 + 脂肪最多占总热量的比例，剩下的留给碳水。 */
        const val MAX_PROTEIN_FAT_SHARE = 0.85f
        const val FAT_CALORIE_CEILING = 0.35f
        /** 极端低热量时的脂肪/蛋白质下限，保证不出现负数碳水。 */
        const val MIN_FAT_PER_KG = 0.4f
        const val MIN_PROTEIN_PER_KG = 1.6f
    }
}