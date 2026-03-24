package com.example.beautyappfrontend.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.Specialist

class SpecialistAdapter(
    private var specialists: List<Specialist>,
    private val onViewClick: ((Specialist) -> Unit)? = null,
    private val onMessageClick: ((Specialist) -> Unit)? = null
) : RecyclerView.Adapter<SpecialistAdapter.SpecialistViewHolder>() {

    class SpecialistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView         = view.findViewById(R.id.tvName)
        val location: TextView     = view.findViewById(R.id.tvLocation)
        val description: TextView  = view.findViewById(R.id.tvDescription)
        val avatar: ImageView      = view.findViewById(R.id.ivAvatar)
        val btnView: Button        = view.findViewById(R.id.btnView)
        val btnMessage: Button     = view.findViewById(R.id.btnMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpecialistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_specialist, parent, false)
        return SpecialistViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpecialistViewHolder, position: Int) {
        val s = specialists[position]
        holder.name.text        = s.name
        holder.location.text    = s.location
        holder.description.text = s.description.ifBlank {
            if (s.specialization.isNotBlank()) s.specialization else "—"
        }

        if (s.imageUrl.isNotBlank()) {
            holder.avatar.imageTintList = null
            holder.avatar.setPadding(0, 0, 0, 0)
            holder.avatar.load(s.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
                transformations(CircleCropTransformation())
            }
        } else {
            val pad = (16 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.avatar.setPadding(pad, pad, pad, pad)
            holder.avatar.setImageResource(R.drawable.ic_nav_profile)
            holder.avatar.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.context, R.color.guava_sage),
            )
        }

        holder.btnView.setOnClickListener    { onViewClick?.invoke(s) }
        holder.btnMessage.setOnClickListener { onMessageClick?.invoke(s) }
    }

    override fun getItemCount() = specialists.size

    fun updateData(newSpecialists: List<Specialist>) {
        specialists = newSpecialists
        notifyDataSetChanged()
    }
}
