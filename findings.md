# 發現與決策

## 需求
- Home 頁面添加按鈕點擊後彈出對話框
- 對話框包含：食物名稱、食用數量（克）、碳水、蛋白質、脂肪、膳食纖維、鈉含量的輸入框
- 熱量字段（可自動計算或手動輸入）
- 底部掃描按鈕，啟動攝像頭掃描營養成分表
- 掃描結果填充到對應字段
- 保存後更新 Home 頁面的營養素顯示

## 研究發現
### 現有代碼結構分析（階段 1）

#### HomeScreen 當前狀態
- 文件位置：`features/home/HomeScreen.kt`
- 已有 FAB 按鈕，但目前 `onNavigateToSearch` 是空回調
- 使用 LazyColumn 展示今日食物條目列表
- 從 HomeViewModel 獲取 uiState

#### FoodEntry 領域模型
- 文件位置：`domain/model/FoodEntry.kt`
- 字段：id, foodItemId, foodName, amountGrams, totalCalories, totalProtein, totalCarbs, totalFat, totalFiber, mealType, dateEpochDay, createdAt, note
- **缺少 sodium（鈉）字段** - 需要添加到模型中

#### FoodItem 領域模型
- 文件位置：`domain/model/FoodItem.kt`
- 字段：id, name, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, fiberPer100g, isCustom, createdAt
- **同樣缺少 sodium 字段**

#### FoodRepository 保存方法
- 文件位置：`data/repository/FoodRepositoryImpl.kt`
- 已有 `addFoodEntry(entry: FoodEntry): Long` 方法
- 通過 FoodEntryDao 插入數據到 Room 資料庫

#### HomeViewModel 數據流
- 文件位置：`features/home/HomeViewModel.kt`
- 使用 `foodRepository.getEntriesByDate(todayEpochDay)` 觀察今日條目
- 通過 Flow 自動更新 UI
- **新增食物後只需調用 repository.addFoodEntry()，UI 會自動刷新**

#### 資料庫實體
- FoodEntryEntity 在 `core/database/entity/FoodEntryEntity.kt`
- FoodItemEntity 在 `core/database/entity/FoodItemEntity.kt`
- **兩個 Entity 都需要添加 sodium 字段**

#### AI API 現有實現
- 文件位置：`core/network/AiApiService.kt`
- 已配置 Anthropic Claude API
- 已有 `AiRepository` 接口和實現
- 可用於解析掃描的營養成分表圖片

## 技術決策
| 決策 | 理由 |
|------|------|
| 熱量自動計算 + 允許手動覆蓋 | 用戶體驗最佳，既提供智能計算又保留靈活性 |
| 使用 AI API 直接解析圖片 | 比傳統 OCR 更準確，能理解營養成分表的結構 |
| 區分自定義食物和資料庫食物 | 未來可以擴展食物庫功能，現在先支持自定義 |
| 暫時不在 Home 頁面顯示鈉含量 | NutrientBar 只顯示主要巨量營養素，鈉可以在詳情頁顯示 |
| 在 HomeViewModel 中添加添加食物的邏輯 | 避免創建新的 ViewModel，保持簡單 |

## 遇到的問題
| 問題 | 解決方案 |
|------|---------||
| FoodEntry 和 FoodItem 缺少 sodium 字段 | 需要修改領域模型、Entity、Mapper 和資料庫 schema |
| 需要相機權限處理 | 使用 Accompanist Permissions 庫或 AndroidX Camera permissions |
| 熱量計算公式 | 使用 Atwater 系統：碳水×4 + 蛋白質×4 + 脂肪×9（**膳食纖維不計入熱量**，因為大部分不吸收）|
| API 級別問題 (LocalDate) | 項目已配置 coreLibraryDesugaring，編譯時會自動處理 |
| CameraAlt 圖標不存在 | 改用 Icons.Default.Camera |
| Home 頁面需要顯示鈉含量 | 在 HomeViewModel 中添加 sodium 的 NutrientState，並在 HomeScreen 中顯示 |
| desugar_jdk_libs 下載超時 | 移除 desugaring 依賴，改用 Calendar 替代 LocalDate，兼容 API 24+ |
| Material Icons 部分圖標不可用 | 使用 Emoji 表情符號代替圖標 |
| 對話框需要全屏顯示 | 設置 DialogProperties(usePlatformDefaultWidth = false) 和 fillMaxSize() |
| 掃描按鈕需要調用攝像頭 | 創建 CameraScanActivity，集成 CameraX，通過 ActivityResultContracts 啟動 |
| 不需要拍照只需實時掃描 | 使用 ML Kit Text Recognition 實現實時文本識別，無需保存圖片 |
| 掃描數據需要回填到表單 | 創建 ScannedNutritionData 數據類，使用本地正則表達式解析 ML Kit 識別的文本，提取營養數值後填充表單 |
| 能量/熱量不從掃描中提取 | **我們自己根據營養素計算**：碳水×4 + 蛋白質×4 + 脂肪×9，不解析能量行 |
| 飽和脂肪酸、反式脂肪酸、糖等子項 | **只提取總脂肪和總碳水**，跳過子項（如"饱和脂肪酸"、"反式脂肪酸"、"糖"）|
| 膳食纖維不計入熱量 | 雖然解析並記錄膳食纖維，但**計算熱量時不包含纖維**（大部分不吸收）|

## 資源
- Jetpack Compose Dialog API
- CameraX 文檔
- Anthropic Claude Vision API
- Room Database Migration

## 視覺/瀏覽器發現
<!-- 關鍵：每執行2次查看/瀏覽器操作後必須更新此部分 -->
<!-- 多模態內容必須立即以文字形式記錄 -->
- 已審查所有相關代碼文件
- 確認了數據流向：UI → ViewModel → Repository → DAO → Room
- 確認了反向數據流：Room → DAO → Repository (Flow) → ViewModel → UI

---
*每執行2次查看/瀏覽器/搜尋操作後更新此檔案*
*防止視覺資訊遺失*
