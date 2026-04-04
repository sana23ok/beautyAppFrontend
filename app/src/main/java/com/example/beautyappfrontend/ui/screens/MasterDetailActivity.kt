package com.example.beautyappfrontend.ui.screens

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityMasterDetailBinding
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import com.example.beautyappfrontend.utils.MasterProfileSchedule
import com.example.beautyappfrontend.utils.MasterScheduleUi
import kotlinx.coroutines.launch

class MasterDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMasterDetailBinding
    private val repository = MasterRepository()

    /** Same 4-week model as master profile; one week shown at a time. */
    private var cachedScheduleWeeks: List<List<List<Int>>> = MasterScheduleData.empty()
    private var scheduleWeekOffset: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMasterDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val masterId = intent.getIntExtra(EXTRA_MASTER_ID, -1)
        if (masterId <= 0) {
            Toast.makeText(this, R.string.master_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fun goBack() {
            val up = NavUtils.getParentActivityIntent(this)
            if (up != null && navigateUpTo(up)) {
                return
            }
            finish()
        }
        binding.btnBack.setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBack()
                }
            },
        )

        binding.btnSchedulePrev.setOnClickListener {
            if (scheduleWeekOffset > 0) {
                scheduleWeekOffset--
                renderScheduleWeek()
            }
        }
        binding.btnScheduleNext.setOnClickListener {
            if (scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1) {
                scheduleWeekOffset++
                renderScheduleWeek()
            }
        }

        binding.progress.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val master = repository.getMasterProfile(masterId)
                bindMaster(master)
                binding.progress.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                Toast.makeText(
                    this@MasterDetailActivity,
                    e.message ?: getString(R.string.load_failed),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    private fun bindMaster(m: MasterProfileResponse) {
        binding.tvName.text = m.name.ifBlank { "—" }
        binding.tvSpecialization.text = m.specialization.ifBlank { "—" }
        binding.tvRating.text = getString(R.string.master_rating_format, m.rating.toDouble())
        val loc = listOf(m.city, m.address).filter { it.isNotBlank() }.joinToString(", ")
        binding.tvLocation.text = loc.ifBlank { "—" }
        binding.tvDescription.text = m.description.ifBlank { getString(R.string.no_description) }
        binding.tvExperience.text = getString(
            R.string.experience_years_format,
            m.experienceYears.coerceAtLeast(0),
        )

        val photo = m.profilePhoto
        if (photo.isNotBlank()) {
            binding.ivPhoto.load(photo) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_nav_profile)
        }

        bindPriceSection(m)

        cachedScheduleWeeks = MasterProfileSchedule.buildScheduleWeeks(m)
        scheduleWeekOffset = 0
        renderScheduleWeek()
    }

    /** Same controls as ProfileActivity master schedule: label + prev/next + horizontal scroll on grid. */
    private fun renderScheduleWeek() {
        binding.tvScheduleWeekLabel.text = MasterScheduleUi.weekRangeLabel(scheduleWeekOffset)
        binding.btnSchedulePrev.isEnabled = scheduleWeekOffset > 0
        binding.btnSchedulePrev.alpha = if (scheduleWeekOffset > 0) 1f else 0.35f
        binding.btnScheduleNext.isEnabled = scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1
        binding.btnScheduleNext.alpha =
            if (scheduleWeekOffset < MasterScheduleData.WEEK_COUNT - 1) 1f else 0.35f
        MasterScheduleUi.populateGrid(
            binding.layoutScheduleGrid,
            scheduleWeekOffset,
            cachedScheduleWeeks,
        )
    }

    /** Always shows the section; empty list → placeholder text. */
    private fun bindPriceSection(m: MasterProfileResponse) {
        val services = m.services.orEmpty().filter { it.name.isNotBlank() || it.price > 0.0 || it.durationMinutes > 0 }
        if (services.isEmpty()) {
            binding.tvPriceList.text = getString(R.string.master_detail_price_empty)
            return
        }
        binding.tvPriceList.text = services.joinToString("\n") { svc ->
            val name = svc.name.trim().ifBlank { "—" }
            val pricePart = when {
                svc.price <= 0.0 -> "—"
                svc.price % 1.0 == 0.0 -> "${svc.price.toInt()} ₴"
                else -> String.format("%s ₴", svc.price)
            }
            val durationPart =
                if (svc.durationMinutes > 0) " (${svc.durationMinutes} min)" else ""
            "$name — $pricePart$durationPart"
        }
    }

    companion object {
        const val EXTRA_MASTER_ID = "master_id"
    }
}
