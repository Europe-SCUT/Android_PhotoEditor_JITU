package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HistoryAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.ivThumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val path = items[position]
        val file = File(path)
        if (file.exists()) {
            // 做一个简单的缩略图加载（防止 OOM）
            val bmp = decodeSampledBitmap(file, 300, 300)
            holder.ivThumb.setImageBitmap(bmp)
        } else {
            holder.ivThumb.setImageBitmap(null)
        }

        holder.itemView.setOnClickListener {
            onClick(path)
        }
    }

    private fun decodeSampledBitmap(file: File, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (width, height) = opts.outWidth to opts.outHeight
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            var halfH = height / 2
            var halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
                halfH /= 2
                halfW /= 2
            }
        }
        return inSampleSize
    }
}
