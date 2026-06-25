package com.example.myapplication.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val planRepo = PlanRepository()

    private val _plans = MutableStateFlow<List<EchoPlan>>(emptyList())
    val plans: StateFlow<List<EchoPlan>> = _plans.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _plans.value = planRepo.getAll().sortedBy { it.triggerAt }
        }
    }

    fun toggleEnabled(planId: String) {
        viewModelScope.launch {
            val plan = planRepo.getById(planId) ?: return@launch
            planRepo.update(plan.copy(enabled = !plan.enabled))
            loadPlans()
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            planRepo.delete(planId)
            loadPlans()
        }
    }

    /** 分组：今日 / 未来 */
    fun getGroupedPlans(): Pair<List<EchoPlan>, List<EchoPlan>> {
        val all = _plans.value
        val now = System.currentTimeMillis()
        val endOfDay = now - (now % 86_400_000) + 86_400_000 - 1
        val today = all.filter { it.triggerAt <= endOfDay && it.enabled }
        val future = all.filter { it.triggerAt > endOfDay || !it.enabled }
        return today to future
    }

    /** 按类型分组 */
    fun getByType(type: String): List<EchoPlan> =
        _plans.value.filter { it.type == type }
}
