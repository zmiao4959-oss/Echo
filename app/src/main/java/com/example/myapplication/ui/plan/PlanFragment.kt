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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlanFragment : Fragment() {

    private lateinit var viewModel: PlanViewModel

    private lateinit var recyclerToday: RecyclerView
    private lateinit var recyclerFuture: RecyclerView
    private lateinit var tvEmptyToday: TextView
    private lateinit var tvEmptyFuture: TextView
    private lateinit var fabAddPlan: FloatingActionButton

    private var todayAdapter: PlanListAdapter? = null
    private var futureAdapter: PlanListAdapter? = null

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

        recyclerToday = view.findViewById(R.id.recycler_today_plans)
        recyclerFuture = view.findViewById(R.id.recycler_future_plans)
        tvEmptyToday = view.findViewById(R.id.tv_empty_today)
        tvEmptyFuture = view.findViewById(R.id.tv_empty_future)
        fabAddPlan = view.findViewById(R.id.fab_add_plan)

        setupAdapters()
        fabAddPlan.setOnClickListener { openEdit(null) }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPlans()
    }

    private fun setupAdapters() {
        todayAdapter = PlanListAdapter(
            onToggle = { plan -> viewModel.toggleEnabled(plan.id) },
            onClick = { plan -> openEdit(plan.id) }
        )
        recyclerToday.layoutManager = LinearLayoutManager(requireContext())
        recyclerToday.adapter = todayAdapter

        futureAdapter = PlanListAdapter(
            onToggle = { plan -> viewModel.toggleEnabled(plan.id) },
            onClick = { plan -> openEdit(plan.id) }
        )
        recyclerFuture.layoutManager = LinearLayoutManager(requireContext())
        recyclerFuture.adapter = futureAdapter
    }

    private fun openEdit(planId: String?) {
        val intent = Intent(requireContext(), PlanEditActivity::class.java)
        if (planId != null) intent.putExtra("plan_id", planId)
        startActivity(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.plans.collectLatest { plans ->
                val (today, future) = viewModel.getGroupedPlans()

                todayAdapter?.submitList(today)
                tvEmptyToday.visibility = if (today.isEmpty()) View.VISIBLE else View.GONE
                recyclerToday.visibility = if (today.isEmpty()) View.GONE else View.VISIBLE

                futureAdapter?.submitList(future)
                tvEmptyFuture.visibility = if (future.isEmpty()) View.VISIBLE else View.GONE
                recyclerFuture.visibility = if (future.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
