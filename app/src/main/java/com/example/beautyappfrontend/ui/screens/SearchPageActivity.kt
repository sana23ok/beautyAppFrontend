package com.example.beautyappfrontend.ui.screens

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.example.beautyappfrontend.databinding.ActivitySearchPageBinding
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.ui.SpecialistAdapter
import com.example.beautyappfrontend.utils.ChatBadgeHelper
import com.example.beautyappfrontend.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class SearchPageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchPageBinding
    private lateinit var adapter: SpecialistAdapter
    private lateinit var session: SessionManager
    private val chatRepository = ChatRepository()

    private var filterLocation       = ""
    private var filterSpecialisation = ""
    private var filterExperience     = ""
    private var filterPriceMax: Int? = null
    private var currentPage          = 1
    private var totalPages           = 1
    private var isLoading            = false

    companion object {
        private const val TAG = "SearchPage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        setupRecyclerView()
        setupSearch()
        setupFilterButton()
        setupPagination()
        setupProfileIcon()
        setupBottomNav()

        fetchMasters()
    }

    override fun onResume() {
        super.onResume()
        ChatBadgeHelper.updateBadge(binding.bottomNav, session.getToken(), lifecycleScope)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = SpecialistAdapter(
            specialists    = emptyList(),
            onViewClick    = { s ->
                openMasterProfile(s)
            },
            onMessageClick = { s ->
                Log.d(TAG, "Message: ${s.name}")
                startConversationWith(s)
            }
        )
        binding.rvSpecialists.layoutManager = LinearLayoutManager(this)
        binding.rvSpecialists.adapter       = adapter
    }

    private fun openMasterProfile(specialist: Specialist) {
        if (specialist.id <= 0) {
            Toast.makeText(this, "Invalid master profile", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MasterDetailActivity::class.java).apply {
            putExtra(MasterDetailActivity.EXTRA_MASTER_ID, specialist.id)
        }
        startActivity(intent)
    }

    private fun startConversationWith(specialist: Specialist) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Please log in to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val participantUserId = specialist.userId
        if (participantUserId == null) {
            Toast.makeText(this, "Cannot message this specialist", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Specialist ${specialist.name} has no user_id")
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@SearchPageActivity, "Starting conversation...", Toast.LENGTH_SHORT).show()

                val response = chatRepository.startConversation(
                    token = token,
                    participantId = participantUserId,
                )

                val intent = Intent(this@SearchPageActivity, ChatConversationActivity::class.java).apply {
                    putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, response.id)
                    putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_NAME, response.participant?.displayName ?: specialist.name)
                    putExtra(ChatConversationActivity.EXTRA_PARTICIPANT_AVATAR, response.participant?.avatar ?: specialist.imageUrl)
                    putExtra(ChatConversationActivity.EXTRA_IS_ONLINE, response.participant?.isOnline ?: false)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting conversation", e)
                Toast.makeText(
                    this@SearchPageActivity,
                    "Failed to start conversation: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun loadSpecialists(list: List<Specialist>) {
        adapter.updateData(list)
        binding.tvResultsCount.text =
            if (list.isEmpty()) "No masters found" else "Results: ${list.size} found"
    }

    private fun fetchMasters(query: String? = null) {
        if (isLoading) return
        isLoading = true

        binding.tvResultsCount.text = "Loading..."

        lifecycleScope.launch {
            try {
                val searchQuery = buildSearchQuery(query)
                Log.d(TAG, "Fetching masters with query: $searchQuery")

                val response = RetrofitInstance.api.searchSpecialists(
                    query = searchQuery.ifBlank { null },
                    page = currentPage,
                )

                if (response.isSuccessful) {
                    val masters = response.body() ?: emptyList()
                    Log.d(TAG, "Fetched ${masters.size} masters from backend")
                    loadSpecialists(masters)
                    updatePaginationFromResults(masters.size)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Failed to fetch masters: HTTP ${response.code()} - $errorBody")
                    loadSpecialists(emptyList())
                    Toast.makeText(
                        this@SearchPageActivity,
                        "Failed to load masters",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching masters", e)
                loadSpecialists(emptyList())
                Toast.makeText(
                    this@SearchPageActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                isLoading = false
            }
        }
    }

    private fun buildSearchQuery(query: String?): String {
        val parts = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            parts.add(query)
        }
        if (filterLocation.isNotBlank()) {
            parts.add(filterLocation)
        }
        if (filterSpecialisation.isNotBlank()) {
            parts.add(filterSpecialisation)
        }
        return parts.joinToString(" ")
    }

    private fun updatePaginationFromResults(count: Int) {
        totalPages = if (count >= 10) 2 else 1
        setupPagination()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.btnSearchGo.setOnClickListener { triggerSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch(); true
            } else false
        }
    }

    private fun triggerSearch() {
        val query = binding.etSearch.text.toString().trim()
        Log.d(TAG, "Search query='$query' loc=$filterLocation spec=$filterSpecialisation " +
                "exp=$filterExperience price=$filterPriceMax")
        currentPage = 1
        fetchMasters(query)
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────────

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener { showFilterSheet() }
    }

    private fun showFilterSheet() {
        val dialog = BottomSheetDialog(this)
        val view   = LayoutInflater.from(this)
            .inflate(R.layout.layout_filter_sheet, null)
        dialog.setContentView(view)

        val etLoc   = view.findViewById<EditText>(R.id.et_filter_location)
        val etSpec  = view.findViewById<EditText>(R.id.et_filter_specialisation)
        val etPrice = view.findViewById<EditText>(R.id.et_filter_price)
        val rgExp   = view.findViewById<RadioGroup>(R.id.rg_experience)
        val btnApply = view.findViewById<Button>(R.id.btn_apply_filters)
        val tvClear  = view.findViewById<TextView>(R.id.tv_clear_all)

        etLoc.setText(filterLocation)
        etSpec.setText(filterSpecialisation)
        etPrice.setText(filterPriceMax?.toString() ?: "")
        when (filterExperience) {
            "1-2" -> rgExp.check(R.id.rb_exp_1_2)
            "3-5" -> rgExp.check(R.id.rb_exp_3_5)
            "5+"  -> rgExp.check(R.id.rb_exp_5plus)
            else  -> rgExp.check(R.id.rb_exp_any)
        }

        tvClear.setOnClickListener {
            etLoc.text.clear(); etSpec.text.clear(); etPrice.text.clear()
            rgExp.check(R.id.rb_exp_any)
        }

        btnApply.setOnClickListener {
            filterLocation       = etLoc.text.toString().trim()
            filterSpecialisation = etSpec.text.toString().trim()
            filterPriceMax       = etPrice.text.toString().toIntOrNull()
            filterExperience     = when (rgExp.checkedRadioButtonId) {
                R.id.rb_exp_1_2   -> "1-2"
                R.id.rb_exp_3_5   -> "3-5"
                R.id.rb_exp_5plus -> "5+"
                else              -> ""
            }
            Log.d(TAG, "Filters: loc=$filterLocation spec=$filterSpecialisation " +
                    "exp=$filterExperience price=$filterPriceMax")
            dialog.dismiss()
            refreshChips()
            currentPage = 1
            fetchMasters(binding.etSearch.text.toString().trim())
        }

        dialog.show()
    }

    // ── Filter chips ──────────────────────────────────────────────────────────

    private fun refreshChips() {
        val container = binding.llChips
        container.removeAllViews()
        var hasAny = false

        fun addChip(label: String) {
            hasAny = true
            val chip = layoutInflater.inflate(
                R.layout.item_filter_chip, container, false
            ) as TextView
            chip.text = label
            container.addView(chip)
        }

        if (filterLocation.isNotEmpty())       addChip("Location: $filterLocation")
        if (filterSpecialisation.isNotEmpty())  addChip("Spec: $filterSpecialisation")
        if (filterExperience.isNotEmpty())      addChip("Exp: $filterExperience yrs")
        if (filterPriceMax != null)             addChip("Max: ${filterPriceMax}₴")

        binding.hsvChips.isVisible = hasAny
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private fun setupPagination() {
        val container = binding.llPagination
        container.removeAllViews()

        if (totalPages <= 1) { container.isVisible = false; return }
        container.isVisible = true

        for (page in 1..totalPages) {
            val tv = TextView(this).apply {
                text     = "Page $page"
                textSize = 13f
                setPadding(24, 12, 24, 12)
                setTypeface(null, if (page == currentPage) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    if (page == currentPage) getColor(R.color.green_dark)
                    else getColor(R.color.guava_sage)
                )
                setOnClickListener {
                    if (currentPage != page) {
                        currentPage = page
                        Log.d(TAG, "Page $page selected")
                        setupPagination()
                        fetchMasters(binding.etSearch.text.toString().trim())
                    }
                }
            }
            container.addView(tv)
        }
    }

    // ── Profile icon ──────────────────────────────────────────────────────────

    private fun setupProfileIcon() {
        binding.ivProfileIcon.setOnClickListener {
            navigateTo(ProfileActivity::class.java)
        }
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_search
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> { navigateTo(HomeActivity::class.java); true }
                R.id.nav_search  -> true
                R.id.nav_chat    -> { navigateTo(ChatActivity::class.java); true }
                R.id.nav_profile -> { navigateTo(ProfileActivity::class.java); true }
                else             -> false
            }
        }
    }

    private fun <T> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }
}
