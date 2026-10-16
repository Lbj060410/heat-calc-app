package com.example.connects.domain.model

/**
 * 体重测量的时段标注。
 *
 * 并非人人都有早上空腹称重的条件，称重常发生在餐后、练后等时段；
 * 记录时打上时段标签便于自查，趋势计算不因时段过滤样本。
 */
enum class  WeightMeasurementContext(val storageKey: String, val label: String) {
    MORNING_FASTING("MORNING_FASTING", "晨起空腹"),
    POST_MEAL("POST_MEAL", "餐后"),
    POST_WORKOUT("POST_WORKOUT", "练后"),
    OTHER("OTHER", "其他时段");

    companion object {
        fun fromStorage(value: String?): WeightMeasurementContext =
            entries.firstOrNull { it.storageKey == value } ?: MORNING_FASTING

        /** 清晨空腹的钟点范围（含边界），如 4..9 表示 04:00–09:59。 */
        private val AUTO_MORNING_RANGE = 4..9

        /**
         * 根据体重录入时的钟点自动推断一个时段，作为手动选择的默认值。
         *
         * 餐后/练后无法仅靠时钟可靠分辨（看不出是否刚吃或刚练），所以只有
         * 「清晨空腹」能被时间准确推断；其余一律归到兜底的「其他时段」
         * （通勤路上、餐后两小时等），再由用户手动改成餐后/练后。
         */
        fun autoInfer(hour: Int): WeightMeasurementContext =
            if (hour in AUTO_MORNING_RANGE) MORNING_FASTING else OTHER
    }
}