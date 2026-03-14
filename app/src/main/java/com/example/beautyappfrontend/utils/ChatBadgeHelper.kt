package com.example.beautyappfrontend.utils

import androidx.core.content.ContextCompat
import com.example.beautyappfrontend.R
import kotlinx.coroutines.CoroutineScope
import com.example.beautyappfrontend.data.repository.ChatRepository
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

object ChatBadgeHelper {

    private val chatRepository = ChatRepository()

    /**
     * Fetches unread count and updates the chat badge on the bottom nav.
     * Shows a red dot when there are unread messages; hides it when seen.
     */
    fun updateBadge(
        bottomNav: BottomNavigationView,
        token: String?,
        scope: CoroutineScope,
    ) {
        if (token.isNullOrBlank()) {
            hideBadge(bottomNav)
            return
        }

        scope.launch {
            try {
                val total = chatRepository.getUnreadTotal(token)
                val badge = bottomNav.getOrCreateBadge(R.id.nav_chat)
                badge.isVisible = total > 0
                badge.backgroundColor = ContextCompat.getColor(bottomNav.context, R.color.red_primary)
                badge.maxCharacterCount = 0
                badge.number = 0
            } catch (e: Exception) {
                hideBadge(bottomNav)
            }
        }
    }

    private fun hideBadge(bottomNav: BottomNavigationView) {
        try {
            val badge = bottomNav.getOrCreateBadge(R.id.nav_chat)
            badge.isVisible = false
        } catch (_: Exception) {}
    }
}
