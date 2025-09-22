package net.programmierecke.radiodroid2.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.programmierecke.radiodroid2.IPlayerService;
import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.station.DataRadioStation;
import net.programmierecke.radiodroid2.utils.GetRealLinkAndPlayTask;

public class MediaSessionCallback extends MediaSessionCompat.Callback {
    public static final String BROADCAST_PLAY_STATION_BY_ID = "PLAY_STATION_BY_ID";
    public static final String EXTRA_STATION_ID = "STATION_ID";
    public static final String ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID";
    public static final String EXTRA_STATION_UUID = "STATION_UUID";

    private Context context;
    private IPlayerService playerService;
    private MediaSessionCompat mediaSession;
    private BroadcastReceiver stationChangeReceiver;

    public MediaSessionCallback(Context context, IPlayerService playerService) {
        this.context = context;
        this.playerService = playerService;
        
        // Register for station change broadcasts to update Recent queue
        setupStationChangeListener();
    }
    
    public void setMediaSession(MediaSessionCompat mediaSession) {
        this.mediaSession = mediaSession;
        // Set up player header navigation links for Android Auto
        if (mediaSession != null) {
            createPlayerHeaderNavigation();
            android.util.Log.i("MediaSessionCallback", "Player header navigation links created for Android Auto");
        }
    }
    
    /**
     * Create Recent stations queue for Android Auto player with icons
     * Populates the queue with recent stations from history, most recent first
     */
    private void createPlayerHeaderNavigation() {
        try {
            RadioDroidApp app = (RadioDroidApp) context.getApplicationContext();
            List<DataRadioStation> recentStations = app.getHistoryManager() != null ? app.getHistoryManager().getList() : null;
            
            if (recentStations != null && !recentStations.isEmpty()) {
                // Limit to 10 most recent stations for performance
                int maxItems = Math.min(10, recentStations.size());
                
                android.util.Log.i("MediaSessionCallback", "Creating Recent queue with icons for " + maxItems + " stations");
                
                // Create queue items with icons loaded asynchronously
                createRecentQueueWithIcons(recentStations, maxItems);
            } else {
                // No recent stations available - create empty queue
                mediaSession.setQueue(null);
                mediaSession.setQueueTitle("Recent");
                android.util.Log.i("MediaSessionCallback", "No recent stations available - created empty Recent queue");
            }
        } catch (Exception e) {
            android.util.Log.w("MediaSessionCallback", "Failed to create Recent queue: " + e.getMessage());
            // Fallback to empty queue
            mediaSession.setQueue(null);
            mediaSession.setQueueTitle("Recent");
        }
    }
    
    /**
     * Create Recent queue items with station icons loaded asynchronously
     */
    private void createRecentQueueWithIcons(List<DataRadioStation> recentStations, int maxItems) {
        List<MediaSessionCompat.QueueItem> queueItems = new ArrayList<>();
        final int[] loadedCount = {0}; // Counter for loaded icons
        
        for (int i = 0; i < maxItems; i++) {
            final DataRadioStation station = recentStations.get(i);
            final int queueIndex = i;
            
            // Create initial queue item without icon
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(RadioDroidBrowser.MEDIA_ID_MUSICS_HISTORY + "|" + station.StationUuid)
                    .setTitle(station.Name)
                    .setSubtitle(station.TagsAll != null ? station.TagsAll : "Recent station");
            
            // Try to load station icon if available
            if (station.IconUrl != null && !station.IconUrl.isEmpty()) {
                // Load icon asynchronously with Picasso
                Picasso.get()
                    .load(station.IconUrl)
                    .resize(128, 128) // Reasonable size for queue icons
                    .centerCrop()
                    .into(new Target() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                            // Apply rounded corners like in the main implementation
                            Bitmap roundedIcon = createRoundedBitmap(bitmap, 6);
                            
                            // Create queue item with icon
                            MediaDescriptionCompat description = descriptionBuilder
                                    .setIconBitmap(roundedIcon)
                                    .build();
                            
                            MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(description, queueIndex);
                            
                            synchronized (queueItems) {
                                // Replace or add the queue item
                                boolean found = false;
                                for (int j = 0; j < queueItems.size(); j++) {
                                    if (queueItems.get(j).getQueueId() == queueIndex) {
                                        queueItems.set(j, queueItem);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    queueItems.add(queueItem);
                                }
                                
                                loadedCount[0]++;
                                
                                // Update queue when all icons are loaded or timeout
                                if (loadedCount[0] >= maxItems) {
                                    updateRecentQueue(queueItems);
                                }
                            }
                        }
                        
                        @Override
                        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                            // Create queue item without icon
                            MediaDescriptionCompat description = descriptionBuilder.build();
                            MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(description, queueIndex);
                            
                            synchronized (queueItems) {
                                queueItems.add(queueItem);
                                loadedCount[0]++;
                                
                                if (loadedCount[0] >= maxItems) {
                                    updateRecentQueue(queueItems);
                                }
                            }
                        }
                        
                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {
                            // Optional: could add placeholder logic here
                        }
                    });
            } else {
                // No icon URL, create queue item without icon
                MediaDescriptionCompat description = descriptionBuilder.build();
                MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(description, queueIndex);
                queueItems.add(queueItem);
                loadedCount[0]++;
            }
        }
        
        // Set a timeout to update queue even if some icons fail to load
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (loadedCount[0] < maxItems) {
                    android.util.Log.w("MediaSessionCallback", "Timeout waiting for Recent queue icons, updating with " + queueItems.size() + " items");
                    updateRecentQueue(queueItems);
                }
            }
        }, 3000); // 3 second timeout
    }
    
    /**
     * Update the MediaSession Recent queue with the provided items
     */
    private void updateRecentQueue(List<MediaSessionCompat.QueueItem> queueItems) {
        if (mediaSession != null) {
            // Sort queue items by queue ID to maintain order
            queueItems.sort((a, b) -> Long.compare(a.getQueueId(), b.getQueueId()));
            
            // Set the queue and title
            mediaSession.setQueue(queueItems);
            mediaSession.setQueueTitle("Recent");
            
            android.util.Log.i("MediaSessionCallback", "Updated Recent queue with " + queueItems.size() + " stations with icons");
        }
    }
    
    /**
     * Create rounded bitmap for Recent queue icons (reused from RadioDroidBrowser)
     */
    private static Bitmap createRoundedBitmap(Bitmap bitmap, int radiusDp) {
        if (bitmap == null) {
            return null;
        }
        
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            // Create output bitmap
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(output);
            
            // Create paint for drawing
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            paint.setColor(0xff424242);
            
            // Convert dp to pixels (approximate)
            float radiusPx = radiusDp * 3; // Rough dp to px conversion
            
            // Create rounded rectangle
            android.graphics.RectF rect = new android.graphics.RectF(0, 0, width, height);
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
            
            // Apply source bitmap with rounded mask
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, 0, 0, paint);
            
            return output;
        } catch (Exception e) {
            android.util.Log.w("MediaSessionCallback", "Failed to create rounded bitmap: " + e.getMessage());
            return bitmap; // Return original bitmap if rounding fails
        }
    }
    
    @Override
    public void onSkipToQueueItem(long queueId) {
        // Handle Recent station selection from queue
        android.util.Log.i("MediaSessionCallback", "Recent station selected from queue: queueId=" + queueId);
        
        try {
            RadioDroidApp app = (RadioDroidApp) context.getApplicationContext();
            List<DataRadioStation> recentStations = app.getHistoryManager() != null ? app.getHistoryManager().getList() : null;
            
            if (recentStations != null && queueId >= 0 && queueId < recentStations.size()) {
                DataRadioStation station = recentStations.get((int) queueId);
                
                // Play the selected recent station
                Intent intent = new Intent(BROADCAST_PLAY_STATION_BY_ID);
                intent.putExtra(EXTRA_STATION_ID, station.StationUuid);
                
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                bm.sendBroadcast(intent);
                
                android.util.Log.i("MediaSessionCallback", "Playing recent station from queue: " + station.Name);
            } else {
                android.util.Log.w("MediaSessionCallback", "Invalid queue ID: " + queueId + " (recent stations: " + 
                    (recentStations != null ? recentStations.size() : 0) + ")");
            }
        } catch (Exception e) {
            android.util.Log.w("MediaSessionCallback", "Failed to play station from Recent queue: " + e.getMessage());
        }
    }
    
    /**
     * Refresh the Recent queue when history changes
     * This should be called when new stations are played to keep the queue up to date
     */
    public void refreshRecentQueue() {
        if (mediaSession != null) {
            android.util.Log.i("MediaSessionCallback", "Refreshing Recent queue due to history change");
            createPlayerHeaderNavigation();
        }
    }
    
    /**
     * Setup listener for station changes to automatically update Recent queue
     */
    private void setupStationChangeListener() {
        stationChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (PlayerService.PLAYER_SERVICE_META_UPDATE.equals(intent.getAction())) {
                    android.util.Log.i("MediaSessionCallback", "Station changed - auto-refreshing Recent queue");
                    refreshRecentQueue();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayerService.PLAYER_SERVICE_META_UPDATE);
        
        LocalBroadcastManager.getInstance(context).registerReceiver(stationChangeReceiver, filter);
        android.util.Log.i("MediaSessionCallback", "Station change listener registered for Recent queue updates");
    }
    
    /**
     * Cleanup method to unregister broadcast receiver
     */
    public void cleanup() {
        if (stationChangeReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(stationChangeReceiver);
                android.util.Log.i("MediaSessionCallback", "Station change listener unregistered");
            } catch (Exception e) {
                android.util.Log.w("MediaSessionCallback", "Failed to unregister station change listener: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
        final KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

        if (event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isLongPress()) {
                try {
                    if (playerService.isPlaying()) {
                        playerService.Pause(PauseReason.USER);
                    } else {
                        playerService.Resume();
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            return true;
        } else {
            return super.onMediaButtonEvent(mediaButtonEvent);
        }
    }

    @Override
    public void onPause() {
        try {
            playerService.Pause(PauseReason.USER);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPlay() {
        try {
            playerService.Resume();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSkipToNext() {
        android.util.Log.i("MediaSessionCallback", "Skip to next - changing station only (no view navigation)");
        try {
            playerService.SkipToNext();
            // NOTE: Do NOT trigger any MediaBrowser navigation here
            // Android Auto should stay in current view (player/browser)
        } catch (RemoteException e) {
            android.util.Log.e("MediaSessionCallback", "Failed to skip to next: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onSkipToPrevious() {
        android.util.Log.i("MediaSessionCallback", "Skip to previous - changing station only (no view navigation)");
        try {
            playerService.SkipToPrevious();
            // NOTE: Do NOT trigger any MediaBrowser navigation here
            // Android Auto should stay in current view (player/browser)
        } catch (RemoteException e) {
            android.util.Log.e("MediaSessionCallback", "Failed to skip to previous: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        try {
            playerService.Stop();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
        final String stationId = RadioDroidBrowser.stationIdFromMediaId(mediaId);

        if (!stationId.isEmpty()) {
            Intent intent = new Intent(BROADCAST_PLAY_STATION_BY_ID);
            intent.putExtra(EXTRA_STATION_ID, stationId);

            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(intent);
            
            // Standard Android Auto behavior - station selection goes to player view
            android.util.Log.i("MediaSessionCallback", "Station playback started from MediaBrowser selection");
        }
    }
    

    @Override
    public void onPlayFromSearch(String query, Bundle extras) {
        DataRadioStation station = ((RadioDroidApp) context.getApplicationContext()).getFavouriteManager().getBestNameMatch(query);
        if (station == null)
           station = ((RadioDroidApp) context.getApplicationContext()).getHistoryManager().getBestNameMatch(query);
        if (station != null) {
            GetRealLinkAndPlayTask playTask = new GetRealLinkAndPlayTask(context, station, playerService);
            playTask.execute();
        }
    }
}