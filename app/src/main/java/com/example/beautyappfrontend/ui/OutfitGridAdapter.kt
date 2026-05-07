package com.example.beautyappfrontend.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.utils.OutfitIdeasHelper

class OutfitGridAdapter(
    private val items: List<OutfitIdeasHelper.OutfitPhotoEntry>,
    private val baseUrl: String,
) : RecyclerView.Adapter<OutfitGridAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivOutfitPhoto)
        val tvTitle: TextView = view.findViewById(R.id.tvOutfitTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_outfit_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.tvTitle.text = entry.item
        val url = "$baseUrl/${entry.image}"
        holder.ivPhoto.load(url) {
            crossfade(true)
            placeholder(R.drawable.bg_option_box)
        }
    }

    override fun getItemCount(): Int = items.size
}
