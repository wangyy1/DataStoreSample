/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codelab.android.datastore.data

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.createDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private const val USER_PREFERENCES_NAME = "user_preferences"
private const val SORT_ORDER_KEY = "sort_order"

enum class SortOrder {
    NONE,
    BY_DEADLINE,
    BY_PRIORITY,
    BY_DEADLINE_AND_PRIORITY
}

/**
 * Class that handles saving and retrieving user preferences
 */
class UserPreferencesRepository private constructor(context: Context) {

    // DataStore对象
    private val dataStore: DataStore<Preferences> =
        context.createDataStore(
            name = USER_PREFERENCES_NAME,
            migrations = listOf(SharedPreferencesMigration(context, USER_PREFERENCES_NAME)) // 用于迁移数据
        )

    // 存储时使用的Key
    private object PreferencesKeys {
        // Note: this has the the same name that we used with SharedPreferences.
        val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }



    // DataStore变化Flow
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            // Get the sort order from preferences and convert it to a [SortOrder] object
            val sortOrder =
                SortOrder.valueOf(
                    preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.NONE.name)

            // Get our show completed value, defaulting to false if not set:
            val showCompleted = preferences[PreferencesKeys.SHOW_COMPLETED] ?: false
            UserPreferences(showCompleted, sortOrder)
        }

    // 编辑
    suspend fun updateShowCompleted(showCompleted: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_COMPLETED] = showCompleted
        }
    }

    // 编辑
    suspend fun enableSortByDeadline(enable: Boolean) {
        // edit handles data transactionally, ensuring that if the sort is updated at the same
        // time from another thread, we won't have conflicts
        dataStore.edit { preferences ->
            // Get the current SortOrder as an enum
            val currentOrder = SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.NONE.name
            )

            val newSortOrder =
                if (enable) {
                    if (currentOrder == SortOrder.BY_PRIORITY) {
                        SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        SortOrder.BY_DEADLINE
                    }
                } else {
                    if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        SortOrder.BY_PRIORITY
                    } else {
                        SortOrder.NONE
                    }
                }
            preferences[PreferencesKeys.SORT_ORDER] = newSortOrder.name
        }
    }

    // 编辑
    suspend fun enableSortByPriority(enable: Boolean) {
        // edit handles data transactionally, ensuring that if the sort is updated at the same
        // time from another thread, we won't have conflicts
        dataStore.edit { preferences ->
            // Get the current SortOrder as an enum
            val currentOrder = SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.NONE.name
            )

            val newSortOrder =
                if (enable) {
                    if (currentOrder == SortOrder.BY_DEADLINE) {
                        SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        SortOrder.BY_PRIORITY
                    }
                } else {
                    if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        SortOrder.BY_DEADLINE
                    } else {
                        SortOrder.NONE
                    }
                }
            preferences[PreferencesKeys.SORT_ORDER] = newSortOrder.name
        }
    }



//    private val sharedPreferences =
//        context.applicationContext.getSharedPreferences(USER_PREFERENCES_NAME, Context.MODE_PRIVATE)
//
//    // Keep the sort order as a stream of changes
//    private val _sortOrderFlow = MutableStateFlow(sortOrder)
//    val sortOrderFlow: StateFlow<SortOrder> = _sortOrderFlow
//
//    /**
//     * Get the sort order. By default, sort order is None.
//     */
//    private val sortOrder: SortOrder
//        get() {
//            val order = sharedPreferences.getString(SORT_ORDER_KEY, SortOrder.NONE.name)
//            return SortOrder.valueOf(order ?: SortOrder.NONE.name)
//        }
//
//    fun enableSortByDeadline(enable: Boolean) {
//        val currentOrder = sortOrderFlow.value
//        val newSortOrder =
//            if (enable) {
//                if (currentOrder == SortOrder.BY_PRIORITY) {
//                    SortOrder.BY_DEADLINE_AND_PRIORITY
//                } else {
//                    SortOrder.BY_DEADLINE
//                }
//            } else {
//                if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
//                    SortOrder.BY_PRIORITY
//                } else {
//                    SortOrder.NONE
//                }
//            }
//        updateSortOrder(newSortOrder)
//        _sortOrderFlow.value = newSortOrder
//    }
//
//    fun enableSortByPriority(enable: Boolean) {
//        val currentOrder = sortOrderFlow.value
//        val newSortOrder =
//            if (enable) {
//                if (currentOrder == SortOrder.BY_DEADLINE) {
//                    SortOrder.BY_DEADLINE_AND_PRIORITY
//                } else {
//                    SortOrder.BY_PRIORITY
//                }
//            } else {
//                if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
//                    SortOrder.BY_DEADLINE
//                } else {
//                    SortOrder.NONE
//                }
//            }
//        updateSortOrder(newSortOrder)
//        _sortOrderFlow.value = newSortOrder
//    }
//
//    private fun updateSortOrder(sortOrder: SortOrder) {
//        sharedPreferences.edit {
//            putString(SORT_ORDER_KEY, sortOrder.name)
//        }
//    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = UserPreferencesRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
