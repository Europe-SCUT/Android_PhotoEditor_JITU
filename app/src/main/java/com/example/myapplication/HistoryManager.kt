package com.example.myapplication

import android.content.Context

object HistoryManager {

    private const val PREF_NAME = "history_prefs"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 50

    fun addHistory(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY_HISTORY, "") ?: ""
        val list = if (old.isBlank()) mutableListOf<String>() else old.split('\n').toMutableList()

        // 去重：如果已有同一路径，先删掉旧的，再插入到最前
        list.remove(path)
        list.add(0, path)

        // 限制长度
        if (list.size > MAX_HISTORY) {
            while (list.size > MAX_HISTORY) {
                list.removeLast()
            }
        }

        val newStr = list.joinToString("\n")
        prefs.edit().putString(KEY_HISTORY, newStr).apply()
    }

    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }
}
