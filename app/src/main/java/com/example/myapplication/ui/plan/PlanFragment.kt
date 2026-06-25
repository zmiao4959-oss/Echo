package com.example.myapplication.ui.plan

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MyApplication
import com.example.myapplication.R
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.ui.PageTextureManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlanFragment : Fragment() {

    private lateinit var viewModel: PlanViewModel

    private lateinit var fabAddPlan: FloatingActionButton

    // RecyclerViews per type
    private lateinit var recyclerReminder: RecyclerView
    private lateinit var recyclerCheckin: RecyclerView
    private lateinit var recyclerMemory: RecyclerView
    private lateinit var recyclerAutodiary: RecyclerView
    private lateinit var tvEmptyReminder: TextView
    private lateinit var tvEmptyCheckin: TextView
    private lateinit var tvEmptyMemory: TextView
    private lateinit var tvEmptyAutodiary: TextView

    private var reminderAdapter: PlanListAdapter? = null
    private var checkinAdapter: PlanListAdapter? = null
    private var memoryAdapter: PlanListAdapter? = null
    private var autodiaryAdapter: PlanListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application as MyApplication)
        )[PlanViewModel::class.java]

        recyclerReminder = view.findViewById(R.id.recycler_reminder_plans)
        recyclerCheckin = view.findViewById(R.id.recycler_checkin_plans)
        recyclerMemory = view.findViewById(R.id.recycler_memory_plans)
        recyclerAutodiary = view.findViewById(R.id.recycler_autodiary_plans)
        tvEmptyReminder = view.findViewById(R.id.tv_empty_reminder)
        tvEmptyCheckin = view.findViewById(R.id.tv_empty_checkin)
        tvEmptyMemory = view.findViewById(R.id.tv_empty_memory)
        tvEmptyAutodiary = view.findViewById(R.id.tv_empty_autodiary)
        fabAddPlan = view.findViewById(R.id.fab_add_plan)

        setupAdapters()
        fabAddPlan.setOnClickListener { openEdit(null) }

        observeViewModel()
        applyPageTexture()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPlans()
        applyPageTexture()
    }

    private fun applyPageTexture() {
        val config = (requireActivity().application as MyApplication).appConfig
        val key = config.getPageTextureKey(PageTextureManager.PLAN_PAGE)
        PageTextureManager.apply(requireView(), key)
    }

    private fun setupAdapters() {
        val onToggle: (EchoPlan) -> Unit = { plan -> viewModel.toggleEnabled(plan.id) }
        val onClick: (EchoPlan) -> Unit = { plan -> openEdit(plan.id) }

        reminderAdapter = PlanListAdapter(onToggle = onToggle, onClick = onClick)
        recyclerReminder.layoutManager = LinearLayoutManager(requireContext())
        recyclerReminder.adapter = reminderAdapter

        checkinAdapter = PlanListAdapter(onToggle = onToggle, onClick = onClick)
        recyclerCheckin.layoutManager = LinearLayoutManager(requireContext())
        recyclerCheckin.adapter = checkinAdapter

        memoryAdapter = PlanListAdapter(onToggle = onToggle, onClick = onClick)
        recyclerMemory.layoutManager = LinearLayoutManager(requireContext())
        recyclerMemory.adapter = memoryAdapter

        autodiaryAdapter = PlanListAdapter(onToggle = onToggle, onClick = onClick)
        recyclerAutodiary.layoutManager = LinearLayoutManager(requireContext())
        recyclerAutodiary.adapter = autodiaryAdapter
    }

    private fun openEdit(planId: String?) {
        val intent = Intent(requireContext(), PlanEditActivity::class.java)
        if (planId != null) intent.putExtra("plan_id", planId)
        startActivity(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.plans.collectLatest {
                updateSection(
                    viewModel.getByType("task_reminder"),
                    reminderAdapter, recyclerReminder, tvEmptyReminder
                )
                updateSection(
                    viewModel.getByType("companion_checkin"),
                    checkinAdapter, recyclerCheckin, tvEmptyCheckin
                )
                updateSection(
                    viewModel.getByType("memory_trigger"),
                    memoryAdapter, recyclerMemory, tvEmptyMemory
                )
                updateSection(
                    viewModel.getByType("auto_diary"),
                    autodiaryAdapter, recyclerAutodiary, tvEmptyAutodiary
                )
            }
        }
    }

    private fun updateSection(
        plans: List<EchoPlan>,
        adapter: PlanListAdapter?,
        recycler: RecyclerView,
        emptyView: TextView
    ) {
        adapter?.submitList(plans)
        emptyView.visibility = if (plans.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (plans.isEmpty()) View.GONE else View.VISIBLE
    }
}
