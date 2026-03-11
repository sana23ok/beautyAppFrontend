package com.example.beautyappfrontend.ui.screens

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import com.example.beautyappfrontend.data.repository.SpecialistRepository
import com.example.beautyappfrontend.ui.MainViewModel
import com.example.beautyappfrontend.ui.MainViewModelFactory
import com.example.beautyappfrontend.ui.SpecialistAdapter

class SearchActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SpecialistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.rvItems)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SpecialistAdapter(emptyList())
        recyclerView.adapter = adapter

        val repository = SpecialistRepository(RetrofitInstance.api)
        val factory = MainViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.specialists.observe(this) { list ->
            adapter.updateData(list)
        }
    }
}
