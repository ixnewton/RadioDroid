package net.programmierecke.radiodroid2.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import androidx.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import android.text.TextUtils;

import androidx.media.utils.MediaConstants;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import net.programmierecke.radiodroid2.R;
import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.service.PlayerServiceUtil;
import net.programmierecke.radiodroid2.station.DataRadioStation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jp.wasabeef.picasso.transformations.CropCircleTransformation;
import jp.wasabeef.picasso.transformations.CropSquareTransformation;
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

import static net.programmierecke.radiodroid2.Utils.resourceToUri;


public class RadioDroidBrowser {
    public static final String MEDIA_ID_ROOT = "__ROOT__";
    public static final String MEDIA_ID_MUSICS_FAVORITE = "FAVORITE";
    public static final String MEDIA_ID_MUSICS_HISTORY = "HISTORY";
    public static final String MEDIA_ID_RECOMMENDED = "__RECOMMENDED__"; // Android Auto recommendations
    public static final String MEDIA_ID_MUSICS_TOP = "__TOP__";
    public static final String MEDIA_ID_MUSICS_TOP_TAGS = "__TOP_TAGS__";

    private static final char LEAF_SEPARATOR = '|';

    private static final int IMAGE_LOAD_TIMEOUT_MS = 2000;

    private RadioDroidApp radioDroidApp;

    private Map<String, DataRadioStation> stationIdToStation = new HashMap<>();

    private static class RetrieveStationsIconAndSendResult extends AsyncTask<Void, Void, Void> {
        private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result;
        private List<DataRadioStation> stations;
        private WeakReference<Context> contextRef;
        private String parentId;

        private Map<String, Bitmap> stationIdToIcon = new HashMap<>();
        private CountDownLatch countDownLatch;
        private  Resources resources;
        // Picasso stores weak references to targets
        List<Target> imageLoadTargets = new ArrayList<>();

        RetrieveStationsIconAndSendResult(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result, List<DataRadioStation> stations, Context context, String parentId) {
            this.result = result;
            this.stations = stations;
            this.contextRef = new WeakReference<>(context);
            this.parentId = parentId;
            resources = context.getApplicationContext().getResources();
        }

        @Override
        protected void onPreExecute() {
            countDownLatch = new CountDownLatch(stations.size());

            for (final DataRadioStation station : stations) {
                Context context = contextRef.get();
                if (context == null) {
                    break;
                }

                Target imageLoadTarget = new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        stationIdToIcon.put(station.StationUuid, bitmap);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        onBitmapLoaded(((BitmapDrawable) errorDrawable).getBitmap(), null);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                };
                imageLoadTargets.add(imageLoadTarget);

                Picasso.get().load((!station.hasIcon() ? resourceToUri(resources, R.drawable.ic_launcher).toString() : station.IconUrl))
                        .transform(new CropSquareTransformation())
                        .error(R.drawable.ic_launcher)
                        .transform(Utils.useCircularIcons(context) ? new CropCircleTransformation() : new CropSquareTransformation())
                        .transform(new RoundedCornersTransformation(12, 2, RoundedCornersTransformation.CornerType.ALL))
                        .resize(128, 128)
                        .into(imageLoadTarget);
            }

            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                countDownLatch.await(IMAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            Context context = contextRef.get();
            if (context != null) {
                for (Target target : imageLoadTargets) {
                    Picasso.get().cancelRequest(target);
                }
            }

            List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

            for (DataRadioStation station : stations) {
                Bitmap stationIcon = stationIdToIcon.get(station.StationUuid);
                if (stationIcon == null)
                    stationIcon = BitmapFactory.decodeResource(Resources.getSystem(), R.drawable.ic_launcher);
                Bundle extras = new Bundle();
                extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, stationIcon);
                extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, stationIcon);
                
                // Set content style based on user preference for Android Auto
                Context appContext = contextRef.get();
                if (appContext != null) {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(appContext);
                    boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", false);
                    
                    // Check if this station is currently playing for visual feedback
                    String currentStationUuid = PlayerServiceUtil.getStationId();
                    boolean isCurrentlyPlaying = station.StationUuid.equals(currentStationUuid);
                    
                    if (iconsOnlyStyle) {
                        // Use grid layout for icon view - Android Auto standard is 3 columns
                        extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM);
                        
                        // Force 3 columns to match Android Auto standard for media apps
                        extras.putInt("android.media.browse.CONTENT_STYLE_GRID_COLUMNS", 3);
                        
                        if (isCurrentlyPlaying) {
                            android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to GRID view (3 columns) for CURRENTLY PLAYING station: " + station.Name);
                            // Add visual indicator for currently playing station
                            extras.putString("android.media.browse.CONTENT_STYLE_PLAYING_INDICATOR", "true");
                        } else {
                            android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to GRID view (3 columns) for station: " + station.Name);
                        }
                    } else {
                        // Use list layout for list view
                        extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
                        
                        if (isCurrentlyPlaying) {
                            android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to LIST view for CURRENTLY PLAYING station: " + station.Name);
                            // Add visual indicator for currently playing station
                            extras.putString("android.media.browse.CONTENT_STYLE_PLAYING_INDICATOR", "true");
                        } else {
                            android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to LIST view for station: " + station.Name);
                        }
                    }
                }
                
                // Determine correct MediaId based on parent context
                String mediaId;
                if (parentId.equals(MEDIA_ID_MUSICS_FAVORITE)) {
                    mediaId = MEDIA_ID_MUSICS_FAVORITE + LEAF_SEPARATOR + station.StationUuid;
                } else {
                    mediaId = MEDIA_ID_MUSICS_HISTORY + LEAF_SEPARATOR + station.StationUuid;
                }
                
                // Apply rounded corners to icon for Android Auto
                Bitmap roundedIcon = createRoundedBitmap(stationIcon, 6); // 6dp radius to match mobile app
                
                mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                        .setMediaId(mediaId)
                        .setTitle(station.Name) // Show station names in Android Auto grid view
                        .setIconBitmap(roundedIcon)
                        .setExtras(extras)
                        .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            }

            result.sendResult(mediaItems);

            super.onPostExecute(aVoid);
        }
    }

    public RadioDroidBrowser(RadioDroidApp radioDroidApp) {
        this.radioDroidApp = radioDroidApp;
    }

    @Nullable
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        android.util.Log.d("RadioDroidBrowser", "onGetRoot: package=" + clientPackageName + ", uid=" + clientUid);
        
        // Allow Android Auto and other trusted media clients
        if (isValidPackage(clientPackageName, clientUid)) {
            android.util.Log.d("RadioDroidBrowser", "Allowing access for: " + clientPackageName);
            
            // Create extras bundle to hint Android Auto to prefer browse view over player view
            Bundle extras = new Bundle();
            extras.putBoolean("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", true);
            extras.putBoolean("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", false);
            
            // Add hint to show favorites as default view
            extras.putString("android.media.browse.DEFAULT_TAB", MEDIA_ID_MUSICS_FAVORITE);
            
            android.util.Log.d("RadioDroidBrowser", "Setting Android Auto to prefer browse view with favorites default");
            return new MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, extras);
        }
        
        // Return empty root for untrusted clients (they can connect but can't browse)
        android.util.Log.d("RadioDroidBrowser", "Denying browse access for: " + clientPackageName);
        return new MediaBrowserServiceCompat.BrowserRoot("__EMPTY_ROOT__", null);
    }
    
    private boolean isValidPackage(String clientPackageName, int clientUid) {
        // Allow Android Auto
        if ("com.google.android.projection.gearhead".equals(clientPackageName)) {
            return true;
        }
        
        // Allow Android Auto for phone screens
        if ("com.google.android.gms".equals(clientPackageName)) {
            return true;
        }
        
        // Allow other common media clients
        if ("com.android.bluetooth".equals(clientPackageName)) {
            return true;
        }
        
        // Allow Wear OS
        if ("com.google.android.wearable.app".equals(clientPackageName)) {
            return true;
        }
        
        // Allow the app itself
        if ("net.programmierecke.radiodroid2".equals(clientPackageName)) {
            return true;
        }
        
        // You can add more trusted package names here
        return false;
    }

    public void onLoadChildren(@NonNull String parentId, @NonNull MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        Resources resources = radioDroidApp.getResources();
        
        // Handle empty root for untrusted clients
        if ("__EMPTY_ROOT__".equals(parentId)) {
            result.sendResult(new ArrayList<>());
            return;
        }
        
        if (MEDIA_ID_ROOT.equals(parentId)) {
            // For Android Auto, check if we should show favorites directly as root content
            RadioDroidApp app = (RadioDroidApp) radioDroidApp;
            if (app.getFavouriteManager() != null && !app.getFavouriteManager().isEmpty()) {
                android.util.Log.d("RadioDroidBrowser", "Android Auto: Showing favorites directly as root content");
                // Show favorites directly instead of root menu for better UX
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp);
                boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", true);
                android.util.Log.i("RadioDroidBrowser", "Android Auto Root - using user preference: iconsOnlyStyle=" + iconsOnlyStyle);
                
                List<DataRadioStation> stations = app.getFavouriteManager().getList();
                if (stations != null && !stations.isEmpty()) {
                    stationIdToStation.clear();
                    for (DataRadioStation station : stations) {
                        stationIdToStation.put(station.StationUuid, station);
                    }
                    result.detach();
                    new RetrieveStationsIconAndSendResult(result, stations, radioDroidApp, MEDIA_ID_MUSICS_FAVORITE).execute();
                    return;
                }
            }
            
            // Fallback to normal root menu if no favorites
            result.sendResult(createBrowsableMediaItemsForRoot(resources));
            return;
        }

        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        List<DataRadioStation> stations = null;

        switch (parentId) {
            case MEDIA_ID_MUSICS_FAVORITE: {
                // Use user's stored preference for Android Auto (default to icon view)
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp);
                boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", true);
                boolean loadIcons = sharedPref.getBoolean("load_icons", true);
                
                android.util.Log.i("RadioDroidBrowser", "Android Auto Favorites - using user preference: iconsOnlyStyle=" + iconsOnlyStyle + ", loadIcons=" + loadIcons);
                
                stations = radioDroidApp.getFavouriteManager().getList();
                break;
            }
            case MEDIA_ID_MUSICS_HISTORY: {
                // History view also defaults to icon view in Android Auto
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp);
                boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", true);
                boolean loadIcons = sharedPref.getBoolean("load_icons", true);
                
                android.util.Log.i("RadioDroidBrowser", "Android Auto History - using icon view: iconsOnlyStyle=" + iconsOnlyStyle + ", loadIcons=" + loadIcons);
                
                stations = radioDroidApp.getHistoryManager().getList();
                break;
            }
            case MEDIA_ID_MUSICS_TOP: {

                break;
            }
            case MEDIA_ID_RECOMMENDED: {
                // Android Auto "For You" recommendations - provide top favorites
                android.util.Log.i("RadioDroidBrowser", "Android Auto Recommendations - providing top favorites for 'For You' view");
                
                // Use favorites as recommendations, limited to top 6 most recently played
                List<DataRadioStation> allFavorites = radioDroidApp.getFavouriteManager().getList();
                if (allFavorites != null && !allFavorites.isEmpty()) {
                    // Limit to 6 recommendations for better performance and UX
                    int maxRecommendations = Math.min(6, allFavorites.size());
                    stations = allFavorites.subList(0, maxRecommendations);
                    android.util.Log.i("RadioDroidBrowser", "Providing " + stations.size() + " recommendations from favorites");
                } else {
                    android.util.Log.i("RadioDroidBrowser", "No favorites available for recommendations");
                }
                break;
            }
        }

        if (stations != null && !stations.isEmpty()) {
            stationIdToStation.clear();
            for (DataRadioStation station : stations) {
                stationIdToStation.put(station.StationUuid, station);
            }
            result.detach();
            new RetrieveStationsIconAndSendResult(result, stations, radioDroidApp, parentId).execute();
        } else {
            result.sendResult(mediaItems);
        }

    }

    @Nullable
    public DataRadioStation getStationById(@NonNull String stationId) {
        return stationIdToStation.get(stationId);
    }

    private List<MediaBrowserCompat.MediaItem> createBrowsableMediaItemsForRoot(Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIconUri(resourceToUri(resources, R.drawable.ic_star_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_HISTORY)
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIconUri(resourceToUri(resources, R.drawable.ic_restore_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_TOP)
                .setTitle(resources.getString(R.string.action_top_click))
                .setIconUri(resourceToUri(resources, R.drawable.ic_restore_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        return mediaItems;
    }

    public static String stationIdFromMediaId(final String mediaId) {
        if (mediaId == null) {
            return "";
        }

        final int separatorIdx = mediaId.indexOf(LEAF_SEPARATOR);

        if (separatorIdx <= 0) {
            return mediaId;
        }

        return mediaId.substring(separatorIdx + 1);
    }
    
    /**
     * Creates a rounded bitmap for Android Auto icons
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
            Canvas canvas = new Canvas(output);
            
            // Create paint for drawing
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(0xff424242);
            
            // Convert dp to pixels (approximate)
            float radiusPx = radiusDp * 3; // Rough dp to px conversion
            
            // Create rounded rectangle
            RectF rect = new RectF(0, 0, width, height);
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
            
            // Apply source bitmap with rounded mask
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, 0, 0, paint);
            
            return output;
        } catch (Exception e) {
            android.util.Log.w("RadioDroidBrowser", "Failed to create rounded bitmap: " + e.getMessage());
            return bitmap; // Return original bitmap if rounding fails
        }
    }
}
