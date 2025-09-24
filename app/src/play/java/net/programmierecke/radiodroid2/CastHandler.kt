package net.programmierecke.radiodroid2

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import net.programmierecke.radiodroid2.cast.CastAwareActivity
import net.programmierecke.radiodroid2.service.PauseReason
import net.programmierecke.radiodroid2.service.PlayerServiceUtil

private sealed class CastState {
    abstract fun setActivity(activity: CastAwareActivity?)

    abstract fun onPause()
    abstract fun onResume()

    abstract fun onSessionStarted(session: Session)
    abstract fun onSessionResumed(session: Session)
    abstract fun onSessionLost()

    abstract fun play(title: String, url: String, iconurl: String?)
}

private object CastUnavailable : CastState() {
    private const val TAG = "CastHandler.CastUnavailable"
    override fun setActivity(activity: CastAwareActivity?) {
    }

    override fun onPause() {
    }

    override fun onResume() {
    }

    override fun onSessionStarted(session: Session) {
        Log.e(TAG, "onSessionStarted: Illegal operation")
    }

    override fun onSessionResumed(session: Session) {
        Log.e(TAG, "onSessionResumed: Illegal operation")
    }

    override fun onSessionLost() {
        Log.e(TAG, "onSessionLost: Illegal operation")
    }

    override fun play(title: String, url: String, iconurl: String?) {
        Log.e(TAG, "play: Illegal operation")
    }
}

private class CastAvailable(val castContext: CastContext,
                            val sessionManager: SessionManager,
                            val sessionManagerListener: SessionManagerListener<Session>,
                            var castSession: CastSession?) : CastState() {
    private var activity: CastAwareActivity? = null
    
    companion object {
        private const val TAG = "CastHandler.CastAvailable"
    }

    override fun setActivity(activity: CastAwareActivity?) {
        this.activity = activity
    }

    override fun onPause() {
        sessionManager.removeSessionManagerListener(sessionManagerListener)
        castSession = null
    }

    override fun onResume() {
        sessionManager.addSessionManagerListener(sessionManagerListener)
        castSession = sessionManager.currentCastSession
    }

    override fun onSessionStarted(session: Session) {
        castSession = sessionManager.currentCastSession

        invalidateOptions()

        if (PlayerServiceUtil.isPlaying()) {
            PlayerServiceUtil.pause(PauseReason.USER)

            val station = PlayerServiceUtil.getCurrentStation()!!
            play(station.Name, station.playableUrl, station.IconUrl)
        }
    }

    override fun onSessionResumed(session: Session) {
        castSession = sessionManager.currentCastSession

        invalidateOptions()
    }

    override fun onSessionLost() {
        castSession = null
        
        Log.i(TAG, "Cast session lost - resetting UI state")
        // Reset UI to reflect local playback state
        invalidateOptions()
    }

    override fun play(title: String, url: String, iconurl: String?) {
        Log.i(TAG, "=== CAST PLAY REQUEST ===")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "URL: $url")
        Log.i(TAG, "Icon URL: $iconurl")
        
        // Validate URL
        if (url.isBlank()) {
            Log.e(TAG, "❌ Cannot cast: URL is blank")
            return
        }
        
        // Ensure URL is properly formatted and validate for Google Home compatibility
        val streamUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        
        Log.i(TAG, "Final stream URL: $streamUrl")
        
        // Validate stream URL accessibility for Google Home
        if (!isValidStreamUrl(streamUrl)) {
            Log.e(TAG, "❌ Invalid stream URL for Cast device: $streamUrl")
            return
        }
        
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        movieMetadata.putString(MediaMetadata.KEY_TITLE, title)
        movieMetadata.putString(MediaMetadata.KEY_ARTIST, "RadioDroid")
        movieMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, "Live Radio Stream")
        
        if (!iconurl.isNullOrEmpty()) {
            try {
                val iconUri = Uri.parse(iconurl)
                movieMetadata.addImage(WebImage(iconUri))
                Log.i(TAG, "Added station icon: $iconurl")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add image to metadata: ${e.message}")
            }
        }

        // Determine content type with Google Home compatibility
        val contentType = getGoogleHomeCompatibleContentType(streamUrl)
        Log.i(TAG, "Content type for Google Home: $contentType")
        
        // Log native M3U8 handling
        if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            Log.i(TAG, "🎵 ✅ M3U8/HLS stream detected - using NATIVE Cast support")
            Log.i(TAG, "📡 Cast device will handle HLS segmentation and adaptive streaming")
            Log.i(TAG, "⚡ No transcoding needed - optimal performance and quality")
            Log.i(TAG, "🔄 Adaptive bitrate streaming will adjust to network conditions")
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(contentType)
                .setMetadata(movieMetadata)
                .build()

        val remoteMediaClient = castSession?.remoteMediaClient
        if (remoteMediaClient != null) {
            Log.i(TAG, "Loading media: $title")
            Log.i(TAG, "Stream URL: $streamUrl")
            Log.i(TAG, "Content Type: $contentType")
            
            // Use MediaLoadRequestData for modern Cast SDK
            val loadRequestData = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(0)
                .build()
            
            Log.i(TAG, "🎵 Attempting to load media on Google Home...")
            Log.i(TAG, "MediaInfo details:")
            Log.i(TAG, "  - Stream URL: ${mediaInfo.contentUrl}")
            Log.i(TAG, "  - Content Type: ${mediaInfo.contentType}")
            Log.i(TAG, "  - Stream Type: ${mediaInfo.streamType}")
            Log.i(TAG, "  - Duration: ${mediaInfo.streamDuration}")
            
            remoteMediaClient.load(loadRequestData).setResultCallback { result ->
                if (result.status.isSuccess) {
                    Log.i(TAG, "✅ Media load request accepted by Cast device")
                    Log.i(TAG, "Waiting for playback to start on Google Home...")
                    
                    // Cast mini controller will automatically appear when media is loaded
                    Log.i(TAG, "🎵 Cast mini controller should appear automatically")
                    
                    // Invalidate options menu to update Cast button
                    invalidateOptions()
                    
                    // Add media status listener for real-time updates
                    remoteMediaClient.addListener(object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Listener {
                        override fun onStatusUpdated() {
                            val mediaStatus = remoteMediaClient.mediaStatus
                            if (mediaStatus != null) {
                                Log.i(TAG, "🔄 Cast media status update:")
                                Log.i(TAG, "  - Player State: ${mediaStatus.playerState}")
                                Log.i(TAG, "  - Idle Reason: ${mediaStatus.idleReason}")
                                Log.i(TAG, "  - Current Time: ${mediaStatus.streamPosition}")
                                Log.i(TAG, "  - Media Info: ${mediaStatus.mediaInfo?.contentId}")
                                
                                // Check for specific error states
                                when (mediaStatus.playerState) {
                                    com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE -> {
                                        when (mediaStatus.idleReason) {
                                            com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR -> {
                                                Log.e(TAG, "❌ Google Home reported playback error")
                                            }
                                            com.google.android.gms.cast.MediaStatus.IDLE_REASON_INTERRUPTED -> {
                                                Log.w(TAG, "⚠️ Playback interrupted on Google Home")
                                            }
                                            com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED -> {
                                                Log.i(TAG, "✅ Playback finished on Google Home")
                                            }
                                        }
                                    }
                                    com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING -> {
                                        Log.i(TAG, "🎵 ✅ Audio is now playing on Google Home!")
                                    }
                                    com.google.android.gms.cast.MediaStatus.PLAYER_STATE_BUFFERING -> {
                                        Log.i(TAG, "⏳ Google Home is buffering stream...")
                                    }
                                    com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PAUSED -> {
                                        Log.i(TAG, "⏸️ Playback paused on Google Home")
                                    }
                                }
                                
                                // Update UI based on cast player state
                                invalidateOptions()
                            }
                        }
                        
                        override fun onMetadataUpdated() {
                            Log.i(TAG, "📋 Cast metadata updated")
                        }
                        
                        override fun onQueueStatusUpdated() {
                            Log.i(TAG, "📝 Cast queue status updated")
                        }
                        
                        override fun onPreloadStatusUpdated() {
                            Log.i(TAG, "⏳ Cast preload status updated")
                        }
                        
                        override fun onSendingRemoteMediaRequest() {
                            Log.i(TAG, "📤 Sending remote media request to Google Home")
                        }
                        
                        override fun onAdBreakStatusUpdated() {
                            Log.i(TAG, "📺 Cast ad break status updated")
                        }
                    })
                } else {
                    Log.e(TAG, "❌ Failed to load media on Google Home")
                    Log.e(TAG, "Status code: ${result.status.statusCode}")
                    Log.e(TAG, "Status message: ${result.status.statusMessage}")
                    Log.e(TAG, "Stream URL: ${mediaInfo.contentUrl}")
                    Log.e(TAG, "Content Type: ${mediaInfo.contentType}")
                    
                    // Provide specific troubleshooting based on error
                    when (result.status.statusCode) {
                        2003 -> Log.e(TAG, "💡 Error 2003: Stream format may not be supported by Google Home")
                        2004 -> Log.e(TAG, "💡 Error 2004: Stream URL may not be accessible by Google Home")
                        else -> Log.e(TAG, "💡 Check if stream URL is accessible and format is supported")
                    }
                }
            }
        } else {
            Log.e(TAG, "❌ RemoteMediaClient is null - Cast session not available")
        }
    }

    private fun invalidateOptions() {
        activity?.invalidateOptionsMenuForCast()
    }
    
    private fun isValidStreamUrl(url: String): Boolean {
        // Basic URL validation for Cast compatibility
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            
            // Must have valid scheme and host
            if (scheme == null || host == null) {
                Log.e(TAG, "Invalid URL scheme or host: $url")
                return false
            }
            
            // Must be HTTP or HTTPS
            if (scheme != "http" && scheme != "https") {
                Log.e(TAG, "Unsupported URL scheme: $scheme")
                return false
            }
            
            // Check for common problematic patterns
            if (url.contains("localhost") || url.contains("127.0.0.1")) {
                Log.e(TAG, "Localhost URLs not accessible by Cast device: $url")
                return false
            }
            
            Log.i(TAG, "✅ Stream URL validation passed: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "URL validation failed: ${e.message}")
            false
        }
    }
    
    private fun getGoogleHomeCompatibleContentType(url: String): String {
        return when {
            // M3U8/HLS - Native Cast support with adaptive streaming
            url.contains(".m3u8", ignoreCase = true) -> {
                Log.i(TAG, "🎵 M3U8/HLS detected - Cast will handle natively with adaptive streaming")
                "application/vnd.apple.mpegurl"
            }
            // Standard audio formats
            url.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            url.contains(".aac", ignoreCase = true) -> "audio/aac"
            url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
            url.contains(".opus", ignoreCase = true) -> "audio/opus"
            url.contains(".flac", ignoreCase = true) -> "audio/flac"
            url.contains(".wav", ignoreCase = true) -> "audio/wav"
            // For unknown formats, use audio/mpeg as it's most widely supported
            else -> {
                Log.i(TAG, "Unknown format, using audio/mpeg for Google Home compatibility")
                "audio/mpeg"
            }
        }
    }
}

public class CastHandler {

    companion object {
        private const val TAG = "CastHandler"
    }

    private var castState: CastState = CastUnavailable

    val isReal: Boolean
        get() = true

    val isCastAvailable: Boolean
        get() = castState is CastAvailable

    val isCastSessionAvailable: Boolean
        get() = (castState as? CastAvailable)?.castSession != null
    
    val isCasting: Boolean
        get() {
            val castAvailable = castState as? CastAvailable
            val remoteMediaClient = castAvailable?.castSession?.remoteMediaClient
            return remoteMediaClient?.hasMediaSession() == true && 
                   remoteMediaClient.mediaStatus?.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING
        }
    
    val isCastConnected: Boolean
        get() = (castState as? CastAvailable)?.castSession?.isConnected == true
    

    fun onCreate(context: Context) {
        if (castState is CastAvailable) {
            return
        }

        // Store application context for broadcasts
        applicationContext = context.applicationContext

        try {
            val googleAPI = GoogleApiAvailability.getInstance()
            val result = googleAPI.isGooglePlayServicesAvailable(context)

            if (result == ConnectionResult.SUCCESS) {
                Log.i(TAG, "Google Play Services available, initializing Cast...")
                
                // Try immediate initialization first for device detection
                try {
                    val castExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
                    val castContext = CastContext.getSharedInstance(context, castExecutor).result
                    initializeCastState(castContext)
                    Log.i(TAG, "✅ Cast context initialized successfully - devices should be detectable")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to initialize Cast context: ${e.message}")
                    Log.i(TAG, "Retrying Cast initialization in background...")
                    
                    // Fallback to background initialization
                    Thread {
                        try {
                            Thread.sleep(2000) // Wait a bit before retry
                            val castExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
                            val castContext = CastContext.getSharedInstance(context, castExecutor).result
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                initializeCastState(castContext)
                                Log.i(TAG, "✅ Cast context initialized on retry - devices should now be detectable")
                            }
                        } catch (retryException: Exception) {
                            Log.e(TAG, "❌ Cast initialization retry failed: ${retryException.message}")
                        }
                    }.start()
                }
            } else {
                Log.w(TAG, "Google Play Services not available: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cast framework: ${e.message}", e)
        }
    }

    private fun initializeCastState(castContext: CastContext) {
        val castState = CastAvailable(
            castContext = castContext,
            sessionManager = castContext.sessionManager,
            sessionManagerListener = SessionManagerListenerImpl(),
            castSession = null
        )

        castState.sessionManager.addSessionManagerListener(castState.sessionManagerListener)
        this.castState = castState
        Log.i(TAG, "Cast framework initialized successfully")
        
        // Cast framework initialized - MediaRouter will handle all UI updates automatically
        Log.i(TAG, "Cast framework initialized - MediaRouter will handle UI states")
    }

    fun setActivity(activity: CastAwareActivity?) {
        castState.setActivity(activity)
        
        // Check if Cast is properly initialized when setting activity
        if (castState !is CastAvailable && activity != null) {
            Log.w(TAG, "Cast not available when setting activity, attempting re-initialization...")
            onCreate(activity as Context)
        }
    }
    
    fun checkCastAvailability(): Boolean {
        val isAvailable = castState is CastAvailable
        Log.i(TAG, "Cast availability check: $isAvailable")
        if (!isAvailable) {
            Log.w(TAG, "Cast devices not detectable - Cast context may not be initialized")
        }
        return isAvailable
    }

    private inline fun <T, reified S : T> sealedIf(sealedInstance: T, block: (S) -> Unit): Any? {
        return (sealedInstance as? S)?.also(block)
    }

    fun onPause() {
        castState.onPause()
    }

    fun onResume() {
        castState.onResume()
    }

    fun getRouteItem(context: Context, menu: Menu): MenuItem {
        // Always show Cast button (starts grey, becomes white when devices found)
        // MediaRouter framework handles all state changes automatically
        return CastButtonFactory.setUpMediaRouteButton(context,
                menu,
                R.id.media_route_menu_item)
    }
    
    fun getCastAppId(): String {
        return "5A97BAE4" // RadioDroid Cast App ID
    }
    
    fun hasAvailableDevices(context: Context): Boolean {
        return try {
            val mediaRouter = androidx.mediarouter.media.MediaRouter.getInstance(context)
            val selector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(getCastAppId()))
                .build()
            val routes = mediaRouter.getRoutes()
            
            // Check if there are any Cast routes available (excluding default route)
            val castRoutes = routes.filter { route ->
                route.matchesSelector(selector) && !route.isDefault
            }
            
            val hasDevices = castRoutes.isNotEmpty()
            Log.i(TAG, "Cast devices available: $hasDevices (found ${castRoutes.size} devices)")
            hasDevices
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for Cast devices: ${e.message}")
            false
        }
    }
    
    // Interface for Cast state change notifications
    interface CastStateChangeListener {
        fun onCastStateChanged()
    }
    
    private var castStateChangeListener: CastStateChangeListener? = null
    private var applicationContext: Context? = null
    
    fun setCastStateChangeListener(listener: CastStateChangeListener?) {
        this.castStateChangeListener = listener
    }
    
    private fun notifyCastStateChanged() {
        castStateChangeListener?.onCastStateChanged()
        
        // Send PlayerService state change broadcast to update play bar
        sendPlayerServiceStateChangeBroadcast()
        
        Log.i(TAG, "Cast state change notification sent")
    }
    
    private fun sendPlayerServiceStateChangeBroadcast() {
        try {
            val context = applicationContext
            if (context != null) {
                val intent = android.content.Intent()
                intent.action = net.programmierecke.radiodroid2.service.PlayerService.PLAYER_SERVICE_STATE_CHANGE
                
                // Get current player state to include in broadcast
                val currentState = net.programmierecke.radiodroid2.service.PlayerServiceUtil.getPlayerState()
                intent.putExtra(net.programmierecke.radiodroid2.service.PlayerService.PLAYER_SERVICE_STATE_EXTRA_KEY, currentState as android.os.Parcelable)
                
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                Log.i(TAG, "PlayerService state change broadcast sent for Cast state change")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send PlayerService state change broadcast: ${e.message}")
        }
    }
    
    fun setupMediaRouteButton(context: Context, button: androidx.mediarouter.app.MediaRouteButton) {
        // Always show Cast button if Cast framework is available, regardless of device detection
        if (castState is CastAvailable) {
            CastButtonFactory.setUpMediaRouteButton(context, button)
            button.visibility = android.view.View.VISIBLE
        } else {
            button.visibility = android.view.View.GONE
        }
    }

    fun playRemote(title: String, url: String, iconurl: String?) {
        Log.i(TAG, title)

        castState.play(title, url, iconurl)
    }

    inner class SessionManagerListenerImpl : SessionManagerListener<Session> {
        override fun onSessionStarting(session: Session) {
            Log.i(TAG, "onSessionStarting")
        }

        override fun onSessionStarted(session: Session, sessionId: String) {
            Log.i(TAG, "onSessionStarted")

            castState.onSessionStarted(session)
            notifyCastStateChanged()
        }

        override fun onSessionStartFailed(session: Session, i: Int) {
            Log.i(TAG, "onSessionStartFailed")
        }

        override fun onSessionEnding(session: Session) {
            Log.i(TAG, "onSessionEnding")
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            Log.i(TAG, "onSessionResumed")

            castState.onSessionResumed(session)
            notifyCastStateChanged()
        }

        override fun onSessionResumeFailed(session: Session, i: Int) {
            Log.i(TAG, "onSessionResumeFailed")

            castState.onSessionLost()
            notifyCastStateChanged()
        }

        override fun onSessionSuspended(session: Session, i: Int) {
            Log.i(TAG, "onSessionSuspended")

            castState.onSessionLost()
            notifyCastStateChanged()
        }

        override fun onSessionEnded(session: Session, error: Int) {
            Log.i(TAG, "onSessionEnded")

            castState.onSessionLost()
            notifyCastStateChanged()
        }

        override fun onSessionResuming(session: Session, s: String) {
            Log.i(TAG, "onSessionResuming")
        }
    }
}