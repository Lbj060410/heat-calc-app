package com.example.connects.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.network.NetworkResult
import com.example.connects.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessageUI(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false
)

@HiltViewModel
class AIVieModel @Inject constructor(
    private val repository: AiRepository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessageUI>>(emptyList())
    val messages: StateFlow<List<ChatMessageUI>> = _messages.asStateFlow()
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        // 添加用户消息
        val userMessage = ChatMessageUI(role = "user", content = message)
        _messages.value = _messages.value + userMessage
        
        // 显示加载状态
        val loadingMessage = ChatMessageUI(
            role = "assistant",
            content = "",
            isLoading = true
        )
        _messages.value = _messages.value + loadingMessage
        _isTyping.value = true
        
        viewModelScope.launch {
            try {
                // 构建历史对话上下文
                val userProfile = "健康追踪用户"
                val foodLog = _messages.value
                    .filter { it.role == "user" && !it.isLoading }
                    .joinToString("\n") { it.content }
                
                val result = repository.generateDietAdvice(userProfile, foodLog)
                
                when (result) {
                    is NetworkResult.Success -> {
                        // 移除加载消息，添加真实响应
                        _messages.value = _messages.value.dropLast(1)
                        _messages.value = _messages.value + ChatMessageUI(
                            role = "assistant",
                            content = result.data
                        )
                    }
                    is NetworkResult.Error -> {
                        // 移除加载消息，显示错误
                        _messages.value = _messages.value.dropLast(1)
                        _error.value = result.exception.message ?: "发送消息失败"
                    }
                    is NetworkResult.Loading -> {
                        // 忽略 Loading 状态
                    }
                }
            } finally {
                _isTyping.value = false
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
}
