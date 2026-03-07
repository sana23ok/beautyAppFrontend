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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.databinding.ActivitySearchPageBinding
import com.example.beautyappfrontend.domain.model.Specialist
import com.example.beautyappfrontend.ui.SpecialistAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog

class SearchPageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchPageBinding
    private lateinit var adapter: SpecialistAdapter

    private var filterLocation       = ""
    private var filterSpecialisation = ""
    private var filterExperience     = ""
    private var filterPriceMax: Int? = null
    private var currentPage          = 1
    private val totalPages           = 4

    companion object {
        private const val TAG = "SearchPage"

        private val PLACEHOLDER_LIST = listOf(
            Specialist(
                id = 1, name = "Anna Kovalenko", specialization = "Makeup Artist",
                rating = 4.9, imageUrl = "", location = "Kyiv",
                description = "Professional makeup artist with 5+ years of experience in bridal and editorial looks."
            ),
            Specialist(
                id = 2, name = "Maria Petrenko", specialization = "Hair Stylist",
                rating = 4.7, imageUrl = "", location = "Lviv",
                description = "Specialises in colour, balayage and creative cuts. Trained in Paris."
            ),
            Specialist(
                id = 3, name = "Olena Sydorenko", specialization = "Nail Technician",
                rating = 4.8, imageUrl = "", location = "Kyiv",
                description = "Gel and acrylic nails, nail art, manicure & pedicure. Booking available weekdays."
            ),
            Specialist(
                id = 4, name = "Iryna Marchenko", specialization = "Brow Artist",
                rating = 4.6, imageUrl = "", location = "Odesa",
                description = "Brow shaping, microblading and lamination. Natural-looking results guaranteed."
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupFilterButton()
        setupPagination()
        setupProfileIcon()
        setupBottomNav()

        loadSpecialists(PLACEHOLDER_LIST)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = SpecialistAdapter(
            specialists    = emptyList(),
            onViewClick    = { s ->
                Log.d(TAG, "View: ${s.name}")
                Toast.makeText(this, "View ${s.name}", Toast.LENGTH_SHORT).show()
            },
            onMessageClick = { s ->
                Log.d(TAG, "Message: ${s.name}")
                Toast.makeText(this, "Message ${s.name}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSpecialists.layoutManager = LinearLayoutManager(this)
        binding.rvSpecialists.adapter       = adapter
    }

    private fun loadSpecialists(list: List<Specialist>) {
        adapter.updateData(list)
        binding.tvResultsCount.text =
            if (list.isEmpty()) "No results found" else "Results: ${list.size} found"
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
        // TODO: call API — RetrofitInstance.api.searchSpecialists(query, ...)
        Toast.makeText(this, "Searching: \"$query\"", Toast.LENGTH_SHORT).show()
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
            // TODO: call API with filters
            Toast.makeText(this, "Filters applied", Toast.LENGTH_SHORT).show()
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
                    currentPage = page
                    Log.d(TAG, "Page $page selected")
                    setupPagination()
                    // TODO: call API for new page
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
