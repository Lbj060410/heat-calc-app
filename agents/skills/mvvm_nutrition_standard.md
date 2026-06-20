# Skill: Connects App Architecture & Nutrient Standards

## Description
指导 Gemini 智能体严格按照 com.example.connects 项目的本地标准，进行三大功能页面开发和数据库操作。防止生成过时、冗余、易闪退的陈旧代码。

## Capabilities
- 自动识别四大营养素（碳水、蛋白质、脂肪、膳食纤维）的本地合并累加机制。
- 强制遵循 MVVM 架构与 Hilt 依赖注入规范。
- 强行约束所有 Room 数据库和 DataStore 写入必须在 Dispatchers.IO 线程池中被 viewModelScope 调度。

## Requirements & Rules
1. 界面开发：必须且只能使用 Jetpack Compose。任何时候禁止生成传统 XML 布局。
2. 线程安全性：遇到网络请求与本地 Room 写入时，必须使用 `try-catch` 或 `runCatching` 兜底，防止格式化导致的 NumberFormatException 闪退。
3. 时间戳标尺：今天的天数必须使用兼容到 API 24 (minSdk 24) 的 `Calendar.getInstance()` 的 Epoch Day 计算逻辑，绝对禁止调用 LocalDate#now。
