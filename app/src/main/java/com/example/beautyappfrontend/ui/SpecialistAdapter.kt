package com.example.beautyappfrontend.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.Specialist

class SpecialistAdapter(private var specialists: List<Specialist>) :
    RecyclerView.Adapter<SpecialistAdapter.SpecialistViewHolder>() {

    class SpecialistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val specialization: TextView = view.findViewById(R.id.tvSpecialization)
        val rating: TextView = view.findViewById(R.id.tvRating)
        val avatar: ImageView = view.findViewById(R.id.ivAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpecialistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_specialist, parent, false)
        return SpecialistViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpecialistViewHolder, position: Int) {
        val specialist = specialists[position]

        holder.name.text = specialist.name
        holder.specialization.text = specialist.specialization
        holder.rating.text = specialist.rating.toString()

        // Пізніше тут буде завантаження фото через Glide:
        // Glide.with(holder.itemView).load(specialist.imageUrl).into(holder.avatar)
    }

    override fun getItemCount() = specialists.size

    fun updateData(newSpecialists: List<Specialist>) {
        specialists = newSpecialists
        notifyDataSetChanged()
    }
}