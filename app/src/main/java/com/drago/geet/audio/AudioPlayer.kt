package com.drago.geet.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.Action
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.getSystemService
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import com.drago.geet.R

@UnstableApi
class AudioPlayer : Service(), Player.Listener, PlaybackStatsListener.Callback {
    private lateinit var player: ExoPlayer
    private var notificationManager: NotificationManager? = null
    private val binder: IBinder = MusicBinder()
    private var playlist: List<Uri>? = null
    private lateinit var mediaSession: MediaSession
    private var isNotificationStarted = false
    private val metadataBuilder = MediaMetadata.Builder()


    inner class MusicBinder : Binder() {
        fun getService(): AudioPlayer = this@AudioPlayer
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            ).build()
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.skipSilenceEnabled = true
        player.addListener(this)
        createNotificationChannel()
        initializeNotificationManager()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.shouldBePlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player.removeListener(this)
        player.stop()
        player.release()
        super.onDestroy()
    }

    private class NotificationActionReceiver(private val player: Player) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE -> player.pause()
                ACTION_PLAY -> player.play()
            }
        }
    }

    private class SessionCallback(private val player: Player) : MediaSession.Callback() {
        override fun onPlay() = player.play()
        override fun onPause() = player.pause()
        override fun onSkipToPrevious() = runCatching(player::forceSeekToPrevious).let { }
        override fun onSkipToNext() = runCatching(player::forceSeekToNext).let { }
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
        override fun onStop() = player.pause()
        override fun onRewind() = player.seekToDefaultPosition()
        override fun onSkipToQueueItem(id: Long) =
            runCatching { player.seekToDefaultPosition(id.toInt()) }.let { }
    }

    private val stateBuilder = PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY
                    or PlaybackState.ACTION_PAUSE
                    or PlaybackState.ACTION_PLAY_PAUSE
                    or PlaybackState.ACTION_STOP
                    or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackState.ACTION_SKIP_TO_NEXT
                    or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM
                    or PlaybackState.ACTION_SEEK_TO
                    or PlaybackState.ACTION_REWIND
        )

    private fun initializeNotificationManager() {
        mediaSession = MediaSession(baseContext, "AudioPlayer")
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(SessionCallback(player))
        mediaSession.setPlaybackState(stateBuilder.build())
        mediaSession.isActive = true
    }

    private fun notification(): Notification {
        val mediaMetadata = player.mediaMetadata

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(applicationContext)
        }
        builder.setSmallIcon(R.drawable.outline_music_note_24)
            .setContentTitle(mediaMetadata.title)
            .setContentText(mediaMetadata.artist)
            .setSubText(mediaMetadata.albumTitle)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(false)
            //.setLargeIcon(getAlbumArt(mediaMetadata.artworkUri!!))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(mediaSession.controller.sessionActivity)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .addAction(
                Action(
                    androidx.media3.ui.R.drawable.exo_ic_skip_previous,
                    "Previous",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            .addAction(
                Action(
                    if (player.shouldBePlaying) androidx.media3.ui.R.drawable.exo_icon_pause else androidx.media3.ui.R.drawable.exo_icon_play,
                    if (player.shouldBePlaying) "Pause" else "Play",
                    if (player.shouldBePlaying) MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PAUSE
                    ) else MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY
                    )
                )
            )
            .addAction(
                Action(
                    androidx.media3.ui.R.drawable.exo_icon_next,
                    "Next",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
        return builder.build()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            updateMediaSessionQueue(player.currentTimeline)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        val TAG_LOG = "AUDIOPLAYER"
        Log.d(TAG_LOG, "onTimelineChanged: " + timeline.windowCount)
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            updateMediaSessionQueue(timeline)
        }
    }

    private fun updateMediaSessionQueue(timeline: Timeline) {
        val builder = MediaDescription.Builder()
        val currentMediaItemIndex = player.currentMediaItemIndex
        val lastIndex = timeline.windowCount - 1
        var startIndex = currentMediaItemIndex - 7
        var endIndex = currentMediaItemIndex + 7

        if (startIndex < 0) {
            endIndex -= startIndex
        }

        if (endIndex > lastIndex) {
            startIndex -= (endIndex - lastIndex)
            endIndex = lastIndex
        }

        startIndex = startIndex.coerceAtLeast(0)
        mediaSession.setQueue(
            List(endIndex - startIndex + 1) { index ->
                val mediaItem = timeline.getWindow(index + startIndex, Timeline.Window()).mediaItem
                MediaSession.QueueItem(
                    builder
                        .setMediaId(mediaItem.mediaId)
                        .setTitle(mediaItem.mediaMetadata.title)
                        .setSubtitle(mediaItem.mediaMetadata.artist)
                        .setIconUri(mediaItem.mediaMetadata.artworkUri)
                        .build(),
                    (index + startIndex).toLong()
                )
            }
        )
    }

    @SuppressLint("WrongConstant")
    override fun onEvents(player: Player, events: Player.Events) {
        val notification = notification()
        if (player.duration != C.TIME_UNSET) {
            mediaSession.setMetadata(
                metadataBuilder
                    .putText(MediaMetadata.METADATA_KEY_TITLE, player.mediaMetadata.title)
                    .putText(MediaMetadata.METADATA_KEY_ARTIST, player.mediaMetadata.artist)
                    .putText(MediaMetadata.METADATA_KEY_ALBUM, player.mediaMetadata.albumTitle)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration)
                    .build()
            )
        }
//        if (player.playbackState.equals(Player.STATE_ENDED)){
//            playListPosition++
//            playPlaylist()
//        }
        stateBuilder
            .setState(player.playbackState, player.currentPosition, 1f)
            .setBufferedPosition(player.bufferedPosition)

        mediaSession.setPlaybackState(stateBuilder.build())

        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY
            )
        ) {

            if (player.shouldBePlaying && !isNotificationStarted) {
                isNotificationStarted = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //startForegroundService(this@AudioPlayer, intent<AudioPlayer>())
                }
                startForeground(NOTIFICATION_ID, notification)
            } else {
                if (!player.shouldBePlaying) {
                    isNotificationStarted = false
                    stopForeground(false)
                }
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "geet_music_channel_id"
        const val ACTION_PLAY = "com.drago.geet.ACTION_PLAY"
        const val ACTION_PAUSE = "com.drago.geet.ACTION_PAUSE"
        const val EXTRA_SONG_URI = "com.drago.geet.EXTRA_SONG_URI"
        const val EXTRA_PLAYLIST = "com.drago.geet.EXTRA_PLAYLIST"

    }

    private fun playSong(curentPosition: Int) {
        /*        val dataSourceFactory =
            DefaultDataSourceFactory(this, Util.getUserAgent(this, application.packageName))
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(songUri!!))*/
        val mediaItems = playlist!!.map { MediaItem.fromUri(it) }
        player.setMediaItems(mediaItems)
        player.seekToDefaultPosition(curentPosition)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.action
            when (action) {
                ACTION_PLAY -> {
                    val songUri = intent.getParcelableExtra<Uri>(EXTRA_SONG_URI)
                    val newPlaylist = intent.getParcelableArrayListExtra<Uri>(EXTRA_PLAYLIST)
                    if (!newPlaylist.isNullOrEmpty()) {

                        this.playlist = newPlaylist

                        playSong(newPlaylist.indexOf(songUri))
                    }

                }

                ACTION_PAUSE -> pauseSong()
                // Handle other actions if needed
            }
        }

        return START_NOT_STICKY
    }


    private fun pauseSong() {
        player.playWhenReady = false
    }

    private fun createNotificationChannel(): String {
        notificationManager = getSystemService()
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.run {
                if (getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    createNotificationChannel(
                        NotificationChannel(
                            NOTIFICATION_CHANNEL_ID,
                            "Geet",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            setSound(null, null)
                            enableLights(false)
                            enableVibration(false)
                        }
                    )
                }
            }

        }
        return channelId
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        TODO("Not yet implemented")
    }

    private fun getAlbumArt(songUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, songUri)

        val embeddedArt = retriever.embeddedPicture
        if (embeddedArt != null) {
            return BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)
        } else {
            val resourceId = resources.getIdentifier("ic_launcher", "mipmap", packageName)
            return if (resourceId != 0) {
                BitmapFactory.decodeResource(resources, resourceId)
            } else {
                null
            }
        }
    }
}