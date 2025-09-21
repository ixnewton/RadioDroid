package net.programmierecke.radiodroid2.service;

import android.content.Context;
import android.content.Intent;
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

    public MediaSessionCallback(Context context, IPlayerService playerService) {
        this.context = context;
        this.playerService = playerService;
    }
    
    public void setMediaSession(MediaSessionCompat mediaSession) {
        this.mediaSession = mediaSession;
        // Initialize queue with favorites for Android Auto mini player second page
        initializeQueueWithFavorites();
    }
    
    /**
     * Initialize the MediaSession queue as a virtual endpoint for Android Auto
     * Since RadioDroid doesn't have a traditional queue concept (radio stations are played individually),
     * we mirror the favorites list as the "queue" for Android Auto's mini player to read and interact with.
     * This creates a queue endpoint that Android Auto can consume for the second page (left swipe).
     */
    private void initializeQueueWithFavorites() {
        try {
            RadioDroidApp app = (RadioDroidApp) context.getApplicationContext();
            List<DataRadioStation> favorites = app.getFavouriteManager().getList();
            
            if (favorites != null && !favorites.isEmpty()) {
                // Clear any existing queue first to ensure clean state
                mediaSession.setQueue(null);
                
                // Limit to 10 items for performance and UX
                int maxItems = Math.min(10, favorites.size());
                
                // Create queue items with icons asynchronously
                createQueueItemsWithIcons(favorites, maxItems);
            } else {
                // Clear queue if no favorites available
                mediaSession.setQueue(null);
                mediaSession.setQueueTitle(null);
                android.util.Log.i("MediaSessionCallback", "No favorites available - cleared Android Auto queue");
            }
        } catch (Exception e) {
            android.util.Log.w("MediaSessionCallback", "Failed to initialize queue: " + e.getMessage());
        }
    }
    
    /**
     * Create queue items with station icons loaded from local storage or URLs
     */
    private void createQueueItemsWithIcons(List<DataRadioStation> favorites, int maxItems) {
        List<MediaSessionCompat.QueueItem> queueItems = new ArrayList<>();
        final int[] loadedCount = {0}; // Counter for loaded icons
        
        for (int i = 0; i < maxItems; i++) {
            final DataRadioStation station = favorites.get(i);
            final int queueIndex = i;
            
            // Create initial queue item without icon
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(RadioDroidBrowser.MEDIA_ID_MUSICS_FAVORITE + "|" + station.StationUuid)
                    .setTitle(station.Name)
                    .setSubtitle(station.TagsAll);
            
            // Try to load station icon
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
                                    updateQueueWithItems(queueItems);
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
                                    updateQueueWithItems(queueItems);
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
                    android.util.Log.w("MediaSessionCallback", "Timeout waiting for icons, updating queue with " + queueItems.size() + " items");
                    updateQueueWithItems(queueItems);
                }
            }
        }, 3000); // 3 second timeout
    }
    
    /**
     * Update the MediaSession queue with the provided items
     */
    private void updateQueueWithItems(List<MediaSessionCompat.QueueItem> queueItems) {
        if (mediaSession != null) {
            // Sort queue items by queue ID to maintain order
            queueItems.sort((a, b) -> Long.compare(a.getQueueId(), b.getQueueId()));
            
            // Set the queue
            mediaSession.setQueue(queueItems);
            mediaSession.setQueueTitle("Favorites");
            
            android.util.Log.i("MediaSessionCallback", "Updated virtual queue endpoint 'Favorites' for Android Auto with " + queueItems.size() + " stations with icons");
        }
    }
    
    /**
     * Create rounded bitmap for queue icons (reused from RadioDroidBrowser)
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
    
    /**
     * Refresh the virtual queue endpoint when favorites change
     * Since the queue mirrors the favorites list, this should be called when favorites are added, removed, or reordered
     */
    public void refreshQueue() {
        if (mediaSession != null) {
            android.util.Log.i("MediaSessionCallback", "Refreshing virtual queue endpoint due to favorites list change");
            initializeQueueWithFavorites();
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
        try {
            playerService.SkipToNext();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSkipToPrevious() {
        try {
            playerService.SkipToPrevious();
        } catch (RemoteException e) {
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
    public void onSkipToQueueItem(long queueId) {
        // Handle selection from Android Auto mini player queue endpoint (virtual queue mirroring favorites)
        android.util.Log.i("MediaSessionCallback", "Station selected from Android Auto virtual queue (favorites mirror): queueId=" + queueId);
        
        try {
            RadioDroidApp app = (RadioDroidApp) context.getApplicationContext();
            List<DataRadioStation> favorites = app.getFavouriteManager().getList();
            
            if (favorites != null && queueId >= 0 && queueId < favorites.size()) {
                DataRadioStation station = favorites.get((int) queueId);
                
                Intent intent = new Intent(BROADCAST_PLAY_STATION_BY_ID);
                intent.putExtra(EXTRA_STATION_ID, station.StationUuid);
                
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                bm.sendBroadcast(intent);
                
                android.util.Log.i("MediaSessionCallback", "Playing station from queue: " + station.Name);
            } else {
                android.util.Log.w("MediaSessionCallback", "Invalid queue ID: " + queueId);
            }
        } catch (Exception e) {
            android.util.Log.w("MediaSessionCallback", "Failed to play queue item: " + e.getMessage());
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