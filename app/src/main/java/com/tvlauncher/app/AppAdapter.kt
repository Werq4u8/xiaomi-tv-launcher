package com.tvlauncher.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var onFocusChange: ((String) -> Unit)? = null

    fun setOnFocusChangeListener(listener: (String) -> Unit) {
        onFocusChange = listener
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        val root: View = view.findViewById(R.id.card_root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label

        // Клик — запуск приложения
        holder.root.setOnClickListener { onClick(app) }

        // Фокус — обновляем заголовок (большое название)
        holder.root.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                onFocusChange?.invoke(app.label)
                // Анимация масштаба при фокусе
                holder.root.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(150)
                    .start()
            } else {
                holder.root.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    override fun getItemCount() = apps.size
}
