package com.example.beautyappfrontend.ui.screens

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityMasterDetailBinding
import com.example.beautyappfrontend.domain.model.MasterProfileResponse
import kotlinx.coroutines.launch

class MasterDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMasterDetailBinding
    private val repository = MasterRepository()

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

        binding.btnBack.setOnClickListener { finish() }

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
    }

    companion object {
        const val EXTRA_MASTER_ID = "master_id"
    }
}
