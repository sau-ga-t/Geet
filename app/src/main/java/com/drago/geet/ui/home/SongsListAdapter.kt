package com.drago.geet.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drago.geet.audio.AudioPlayer
import com.drago.geet.databinding.ItemSongBinding
import com.drago.geet.models.Song

class SongListAdapter(private var context: Context) : ListAdapter<Song, SongListAdapter.SongViewHolder>(AppDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val currentSong = getItem(position)
        holder.bind(currentSong)
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener{
        init {
            itemView.setOnClickListener(this)
        }
        fun bind(song: Song) {
            binding.titleTextView.text = song.title
            binding.artistTextView.text = song.artist
            binding.albumTextView.text = song.album
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun onClick(v: View?) {
            val songItem = getItem(adapterPosition)
            val songUri = songItem.uri// provide the URI of the song you want to play
            val intent = Intent(context, AudioPlayer::class.java).apply {
                action = AudioPlayer.ACTION_PLAY
                putExtra(AudioPlayer.EXTRA_SONG_URI, songUri)
                putExtra(AudioPlayer.EXTRA_PLAYLIST, getUrisOfCurrentList())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
               context. startService(intent)
            }
            Log.d("SELECTEDSONG", "onClick: ${songItem.uri}")
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }

    fun getUrisOfCurrentList():ArrayList<Uri>{
        return currentList.map { it.uri } as ArrayList<Uri>
    }
    fun updateList(songs:List<Song>){
        Log.d("SongListAdapter", "updateList: ${songs.size}")
        submitList(songs)
    }
}
