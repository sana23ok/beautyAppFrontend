package com.example.beautyappfrontend.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.repository.MasterRepository
import com.example.beautyappfrontend.databinding.ActivityPhotoGalleryBinding
import com.example.beautyappfrontend.databinding.ItemPhotoGalleryPageBinding
import com.example.beautyappfrontend.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * Fullscreen work-photo viewer with swipe (ViewPager2).
 * Launched from the master's own profile (with delete enabled) and from
 * [MasterDetailActivity] (read-only view).
 */
class PhotoGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoGalleryBinding
    private lateinit var session: SessionManager
    private val masterRepository = MasterRepository()

    /** Parallel lists kept in lock-step with the adapter for delete support. */
    private val photoIds = mutableListOf<Int>()
    private val photoUrls = mutableListOf<String>()
    private var canDelete: Boolean = false
    private var anyChange: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        val initialUrls = intent.getStringArrayListExtra(EXTRA_PHOTO_URLS).orEmpty()
        val initialIds = intent.getIntegerArrayListExtra(EXTRA_PHOTO_IDS).orEmpty()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceAtLeast(0)
        canDelete = intent.getBooleanExtra(EXTRA_CAN_DELETE, false)

        if (initialUrls.isEmpty()) {
            finish()
            return
        }

        photoUrls.addAll(initialUrls)
        photoIds.addAll(
            if (initialIds.size == initialUrls.size) initialIds else List(initialUrls.size) { 0 },
        )

        val adapter = PhotoPagerAdapter()
        binding.pagerPhotos.adapter = adapter
        binding.pagerPhotos.setCurrentItem(startIndex.coerceAtMost(photoUrls.size - 1), false)

        updatePositionLabel(binding.pagerPhotos.currentItem)
        binding.pagerPhotos.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updatePositionLabel(position)
                }
            },
        )

        binding.btnGalleryClose.setOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithResult()
        })

        binding.btnGalleryDelete.visibility = if (canDelete) View.VISIBLE else View.GONE
        binding.btnGalleryDelete.setOnClickListener { confirmDeleteCurrent() }
    }

    private fun updatePositionLabel(position: Int) {
        binding.tvGalleryPosition.text = getString(
            R.string.gallery_position_format,
            position + 1,
            photoUrls.size,
        )
    }

    private fun confirmDeleteCurrent() {
        if (!canDelete) return
        val position = binding.pagerPhotos.currentItem
        val photoId = photoIds.getOrNull(position) ?: return
        if (photoId <= 0) {
            Toast.makeText(this, R.string.gallery_delete_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.gallery_delete_title)
            .setMessage(R.string.gallery_delete_message)
            .setPositiveButton(R.string.gallery_delete_confirm) { _, _ -> deleteCurrent(position, photoId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteCurrent(position: Int, photoId: Int) {
        val token = session.getToken()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.gallery_delete_login_required, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnGalleryDelete.isEnabled = false
        lifecycleScope.launch {
            try {
                masterRepository.deleteMyWorkPhoto(token, photoId)
                anyChange = true
                val adapter = binding.pagerPhotos.adapter as? PhotoPagerAdapter ?: return@launch
                photoIds.removeAt(position)
                photoUrls.removeAt(position)
                if (photoUrls.isEmpty()) {
                    finishWithResult()
                    return@launch
                }
                adapter.notifyItemRemoved(position)
                val newPosition = position.coerceAtMost(photoUrls.size - 1)
                updatePositionLabel(newPosition)
            } catch (e: Exception) {
                Toast.makeText(
                    this@PhotoGalleryActivity,
                    e.message ?: getString(R.string.load_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnGalleryDelete.isEnabled = true
            }
        }
    }

    private fun finishWithResult() {
        if (anyChange) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_CHANGED, true))
        }
        finish()
    }

    private inner class PhotoPagerAdapter :
        RecyclerView.Adapter<PhotoPagerAdapter.PageVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val pageBinding = ItemPhotoGalleryPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return PageVH(pageBinding.ivGalleryImage)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val url = photoUrls.getOrNull(position)
            if (url.isNullOrBlank()) {
                holder.image.setImageResource(R.drawable.ic_nav_profile)
                return
            }
            holder.image.load(url) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        }

        override fun getItemCount(): Int = photoUrls.size

        inner class PageVH(val image: ImageView) : RecyclerView.ViewHolder(image.rootView)
    }

    companion object {
        const val EXTRA_PHOTO_URLS = "photo_urls"
        const val EXTRA_PHOTO_IDS = "photo_ids"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_CAN_DELETE = "can_delete"
        const val EXTRA_RESULT_CHANGED = "result_changed"

        fun newIntent(
            context: android.content.Context,
            urls: List<String>,
            ids: List<Int>,
            startIndex: Int,
            canDelete: Boolean,
        ): Intent = Intent(context, PhotoGalleryActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_PHOTO_URLS, ArrayList(urls))
            putIntegerArrayListExtra(EXTRA_PHOTO_IDS, ArrayList(ids))
            putExtra(EXTRA_START_INDEX, startIndex)
            putExtra(EXTRA_CAN_DELETE, canDelete)
        }
    }
}
