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
    public static final String MEDIA_ID_SUGGESTED = "__SUGGESTED__"; // Android Auto mini-player suggestions (UAMP pattern)
    public static final String MEDIA_ID_MUSICS_TOP = "__TOP__";
    public static final String MEDIA_ID_MUSICS_TOP_TAGS = "__TOP_TAGS__";
    
    // Enhanced recommendation categories (UAMP-inspired)
    public static final String MEDIA_ID_POPULAR_STATIONS = "__POPULAR__";
    public static final String MEDIA_ID_BY_GENRE = "__BY_GENRE__";
    public static final String MEDIA_ID_BY_COUNTRY = "__BY_COUNTRY__";
    public static final String MEDIA_ID_TRENDING = "__TRENDING__";

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

                // Use full-size icons for Android Auto - let MediaPlayer handle display sizing
                android.util.Log.d("RadioDroidBrowser", "Loading Android Auto icon at full resolution (128px)");
                
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
                    boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", true);
                    
                    android.util.Log.i("RadioDroidBrowser", "Android Auto MediaItem creation - iconsOnlyStyle=" + iconsOnlyStyle + " for station: " + station.Name + " (parentId=" + parentId + ")");
                    
                    // Check if this station is currently playing for visual feedback
                    String currentStationUuid = PlayerServiceUtil.getStationId();
                    boolean isCurrentlyPlaying = station.StationUuid.equals(currentStationUuid);
                    
                    if (parentId.equals(MEDIA_ID_SUGGESTED)) {
                        // Mini-player suggestions should always use LIST style for better readability (UAMP pattern)
                        extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
                        
                        android.util.Log.i("RadioDroidBrowser", "🎵 MINI-PLAYER ITEM STYLING:");
                        android.util.Log.i("RadioDroidBrowser", "  • Station: " + station.Name);
                        android.util.Log.i("RadioDroidBrowser", "  • Format: LIST (UAMP mini-player optimization)");
                        android.util.Log.i("RadioDroidBrowser", "  • Currently playing: " + isCurrentlyPlaying);
                        android.util.Log.i("RadioDroidBrowser", "  • Icon available: " + (station.IconUrl != null && !station.IconUrl.isEmpty()));
                        
                        if (isCurrentlyPlaying) {
                            android.util.Log.i("RadioDroidBrowser", "  • 🎵 CURRENTLY PLAYING - Adding visual indicator");
                            extras.putString("android.media.browse.CONTENT_STYLE_PLAYING_INDICATOR", "true");
                        }
                    } else if (parentId.equals(MEDIA_ID_MUSICS_FAVORITE) || parentId.equals(MEDIA_ID_MUSICS_HISTORY)) {
                        boolean loadIcons = Utils.shouldLoadIcons(appContext);
                        
                        if (iconsOnlyStyle && loadIcons) {
                            // Use grid layout for icon view
                            extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM);
                            
                            // Set Android Auto grid columns based on user preference
                            int gridColumns = Integer.parseInt(sharedPref.getString("android_auto_icon_size", "3"));
                            extras.putInt("android.media.browse.CONTENT_STYLE_GRID_COLUMNS", gridColumns);
                            
                            android.util.Log.d("RadioDroidBrowser", "Android Auto grid columns: " + gridColumns);
                            
                            if (isCurrentlyPlaying) {
                                android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to GRID view (" + gridColumns + " columns) for CURRENTLY PLAYING station: " + station.Name);
                                extras.putString("android.media.browse.CONTENT_STYLE_PLAYING_INDICATOR", "true");
                            } else {
                                android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to GRID view (" + gridColumns + " columns) for station: " + station.Name);
                            }
                        } else {
                            // Use list layout for list view
                            extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
                            
                            if (isCurrentlyPlaying) {
                                android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to LIST view for CURRENTLY PLAYING station: " + station.Name);
                                extras.putString("android.media.browse.CONTENT_STYLE_PLAYING_INDICATOR", "true");
                            } else {
                                android.util.Log.i("RadioDroidBrowser", "Setting Android Auto to LIST view for station: " + station.Name);
                            }
                        }
                    }
                }
                
                // Determine correct MediaId based on parent context
                String mediaId;
                if (parentId.equals(MEDIA_ID_MUSICS_FAVORITE)) {
                    mediaId = MEDIA_ID_MUSICS_FAVORITE + LEAF_SEPARATOR + station.StationUuid;
                } else if (parentId.equals(MEDIA_ID_SUGGESTED)) {
                    mediaId = MEDIA_ID_SUGGESTED + LEAF_SEPARATOR + station.StationUuid;
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
            
            // Create extras bundle to hint Android Auto content styles (following UAMP pattern)
            Bundle extras = new Bundle();
            extras.putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, false); // Disable search in Android Auto
            extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);
            extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2); // GRID style
            extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1);  // LIST style
            
            // Use full-size media art for optimal quality
            extras.putInt(MediaConstants.BROWSER_ROOT_HINTS_KEY_MEDIA_ART_SIZE_PIXELS, 128);
            android.util.Log.d("RadioDroidBrowser", "Android Auto media art size hint: 128px (full resolution)");
            
            // Add hint to show favorites as default view
            extras.putString("android.media.browse.DEFAULT_TAB", MEDIA_ID_MUSICS_FAVORITE);
            
            // Add suggested content for Android Auto mini-player recommendations (UAMP pattern)
            extras.putString(MediaBrowserServiceCompat.BrowserRoot.EXTRA_SUGGESTED, MEDIA_ID_SUGGESTED);
            android.util.Log.i("RadioDroidBrowser", "=== MINI-PLAYER DEBUG: EXTRA_SUGGESTED hint registered ===");
            android.util.Log.i("RadioDroidBrowser", "MINI-PLAYER: EXTRA_SUGGESTED → " + MEDIA_ID_SUGGESTED);
            
            // Add recent content hint to prominently display recent stations (Microsoft documentation pattern)
            extras.putString(MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT, MEDIA_ID_MUSICS_HISTORY);
            android.util.Log.i("RadioDroidBrowser", "=== RECENT CONTENT DEBUG: EXTRA_RECENT hint registered ===");
            android.util.Log.i("RadioDroidBrowser", "RECENT CONTENT: EXTRA_RECENT → " + MEDIA_ID_MUSICS_HISTORY);
            
            android.util.Log.i("RadioDroidBrowser", "=== ROOT CONFIGURATION COMPLETE ===");
            android.util.Log.i("RadioDroidBrowser", "Android Auto root configured with:");
            android.util.Log.i("RadioDroidBrowser", "  • DEFAULT_TAB: " + MEDIA_ID_MUSICS_FAVORITE);
            android.util.Log.i("RadioDroidBrowser", "  • EXTRA_SUGGESTED: " + MEDIA_ID_SUGGESTED + " (mini-player 2nd pane)");
            android.util.Log.i("RadioDroidBrowser", "  • EXTRA_RECENT: " + MEDIA_ID_MUSICS_HISTORY + " (prominent recent display)");
            android.util.Log.i("RadioDroidBrowser", "  • SEARCH_SUPPORTED: false (safety)");
            android.util.Log.i("RadioDroidBrowser", "=== WAITING FOR ANDROID AUTO REQUESTS ===");
            
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
        
        // DEBUG: Log every onLoadChildren call
        android.util.Log.i("RadioDroidBrowser", "=== onLoadChildren CALLED ===");
        android.util.Log.i("RadioDroidBrowser", "Parent ID: " + parentId);
        android.util.Log.i("RadioDroidBrowser", "Thread: " + Thread.currentThread().getName());
        android.util.Log.i("RadioDroidBrowser", "Timestamp: " + System.currentTimeMillis());
        
        // Handle empty root for untrusted clients
        if ("__EMPTY_ROOT__".equals(parentId)) {
            android.util.Log.i("RadioDroidBrowser", "Returning empty result for untrusted client");
            result.sendResult(new ArrayList<>());
            return;
        }
        
        if (MEDIA_ID_ROOT.equals(parentId)) {
            android.util.Log.i("RadioDroidBrowser", "=== ANDROID AUTO STARTUP - DETERMINING DEFAULT VIEW ===");
            
            RadioDroidApp app = (RadioDroidApp) radioDroidApp;
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp);
            
            // Check user's preferred startup behavior
            List<DataRadioStation> recentStations = app.getHistoryManager() != null ? app.getHistoryManager().getList() : null;
            List<DataRadioStation> favorites = app.getFavouriteManager() != null ? app.getFavouriteManager().getList() : null;
            
            boolean hasRecentStations = recentStations != null && !recentStations.isEmpty();
            boolean hasFavorites = favorites != null && !favorites.isEmpty();
            
            android.util.Log.i("RadioDroidBrowser", "Android Auto startup analysis:");
            android.util.Log.i("RadioDroidBrowser", "- Recent stations: " + hasRecentStations + " (count: " + (recentStations != null ? recentStations.size() : 0) + ")");
            android.util.Log.i("RadioDroidBrowser", "- Favorites: " + hasFavorites + " (count: " + (favorites != null ? favorites.size() : 0) + ")");
            
            // PRIORITY 1: If user has recent stations, show browsable structure for easy access
            if (hasRecentStations) {
                android.util.Log.i("RadioDroidBrowser", "Android Auto startup: Showing browsable structure (Recent + Favorites + History)");
                result.sendResult(createBrowsableMediaItemsForRoot(resources));
                return;
            }
            
            // PRIORITY 2: If user has favorites but no recent, show favorites directly
            if (hasFavorites) {
                android.util.Log.i("RadioDroidBrowser", "Android Auto startup: Showing favorites directly (no recent stations)");
                boolean iconsOnlyStyle = sharedPref.getBoolean("icons_only_favorites_style", true);
                android.util.Log.i("RadioDroidBrowser", "Android Auto startup - using user preference: iconsOnlyStyle=" + iconsOnlyStyle);
                
                stationIdToStation.clear();
                for (DataRadioStation station : favorites) {
                    stationIdToStation.put(station.StationUuid, station);
                }
                result.detach();
                new RetrieveStationsIconAndSendResult(result, favorites, radioDroidApp, MEDIA_ID_MUSICS_FAVORITE).execute();
                return;
            }
            
            // PRIORITY 3: Fallback to browsable structure for new users
            android.util.Log.i("RadioDroidBrowser", "Android Auto startup: New user - showing browsable structure");
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
                // Android Auto "For You" recommendations - provide intelligent recommendations (UAMP-inspired)
                android.util.Log.i("RadioDroidBrowser", "Android Auto Recommendations - providing intelligent recommendations for 'For You' view");
                
                stations = generateSmartRecommendations();
                android.util.Log.i("RadioDroidBrowser", "Providing " + (stations != null ? stations.size() : 0) + " smart recommendations");
                break;
            }
            case MEDIA_ID_SUGGESTED: {
                // Android Auto mini-player suggestions - provide recent stations (UAMP pattern)
                android.util.Log.i("RadioDroidBrowser", "");
                android.util.Log.i("RadioDroidBrowser", "🎵🎵🎵 MINI-PLAYER 2ND PANE REQUEST RECEIVED 🎵🎵🎵");
                android.util.Log.i("RadioDroidBrowser", "=== MEDIA_ID_SUGGESTED CASE TRIGGERED ===");
                android.util.Log.i("RadioDroidBrowser", "📱 Android Auto is requesting mini-player suggestions for 2nd pane");
                android.util.Log.i("RadioDroidBrowser", "📋 Parent ID: " + parentId + " (should be: " + MEDIA_ID_SUGGESTED + ")");
                android.util.Log.i("RadioDroidBrowser", "🎯 Purpose: Populate mini-player swipe-left suggestions pane");
                
                // Use recent/history stations for mini-player suggestions, following UAMP pattern
                android.util.Log.i("RadioDroidBrowser", "");
                android.util.Log.i("RadioDroidBrowser", "📊 CHECKING DATA SOURCES:");
                List<DataRadioStation> recentStations = radioDroidApp.getHistoryManager().getList();
                android.util.Log.i("RadioDroidBrowser", "  • History manager available: " + (radioDroidApp.getHistoryManager() != null));
                android.util.Log.i("RadioDroidBrowser", "  • Recent stations list: " + (recentStations != null ? recentStations.size() + " items" : "null"));
                
                if (recentStations != null && !recentStations.isEmpty()) {
                    android.util.Log.i("RadioDroidBrowser", "");
                    android.util.Log.i("RadioDroidBrowser", "✅ USING RECENT STATIONS (PRIMARY SOURCE):");
                    // Limit to 6 suggestions for optimal mini-player UX (UAMP typically uses 4-8)
                    int maxSuggestions = Math.min(6, recentStations.size());
                    stations = recentStations.subList(0, maxSuggestions);
                    android.util.Log.i("RadioDroidBrowser", "  • Total recent stations: " + recentStations.size());
                    android.util.Log.i("RadioDroidBrowser", "  • Limited to: " + maxSuggestions + " (UAMP optimal range: 4-8)");
                    android.util.Log.i("RadioDroidBrowser", "  • Final suggestions count: " + stations.size());
                    android.util.Log.i("RadioDroidBrowser", "");
                    android.util.Log.i("RadioDroidBrowser", "📋 MINI-PLAYER SUGGESTIONS LIST:");
                    for (int i = 0; i < stations.size(); i++) {
                        DataRadioStation station = stations.get(i);
                        android.util.Log.i("RadioDroidBrowser", "  " + (i + 1) + ". " + station.Name + " (UUID: " + station.StationUuid + ")");
                        android.util.Log.i("RadioDroidBrowser", "     Tags: " + (station.TagsAll != null ? station.TagsAll : "No tags"));
                        android.util.Log.i("RadioDroidBrowser", "     Icon: " + (station.IconUrl != null && !station.IconUrl.isEmpty() ? "Available" : "None"));
                    }
                } else {
                    android.util.Log.i("RadioDroidBrowser", "");
                    android.util.Log.i("RadioDroidBrowser", "⚠️ NO RECENT STATIONS - USING FALLBACK:");
                    android.util.Log.i("RadioDroidBrowser", "  • Reason: No recent stations available in history");
                    android.util.Log.i("RadioDroidBrowser", "  • Fallback: Using favorites (UAMP pattern)");
                    
                    List<DataRadioStation> fallbackFavorites = radioDroidApp.getFavouriteManager().getList();
                    android.util.Log.i("RadioDroidBrowser", "  • Favorites manager available: " + (radioDroidApp.getFavouriteManager() != null));
                    android.util.Log.i("RadioDroidBrowser", "  • Favorites available: " + (fallbackFavorites != null ? fallbackFavorites.size() + " items" : "null"));
                    
                    if (fallbackFavorites != null && !fallbackFavorites.isEmpty()) {
                        int maxFallback = Math.min(4, fallbackFavorites.size());
                        stations = fallbackFavorites.subList(0, maxFallback);
                        android.util.Log.i("RadioDroidBrowser", "");
                        android.util.Log.i("RadioDroidBrowser", "✅ USING FAVORITES (FALLBACK SOURCE):");
                        android.util.Log.i("RadioDroidBrowser", "  • Total favorites: " + fallbackFavorites.size());
                        android.util.Log.i("RadioDroidBrowser", "  • Limited to: " + maxFallback + " (smaller fallback set)");
                        android.util.Log.i("RadioDroidBrowser", "  • Final suggestions count: " + stations.size());
                        android.util.Log.i("RadioDroidBrowser", "");
                        android.util.Log.i("RadioDroidBrowser", "📋 FALLBACK SUGGESTIONS LIST:");
                        for (int i = 0; i < stations.size(); i++) {
                            DataRadioStation station = stations.get(i);
                            android.util.Log.i("RadioDroidBrowser", "  " + (i + 1) + ". " + station.Name + " (UUID: " + station.StationUuid + ")");
                        }
                    } else {
                        android.util.Log.i("RadioDroidBrowser", "");
                        android.util.Log.i("RadioDroidBrowser", "❌ NO CONTENT AVAILABLE:");
                        android.util.Log.i("RadioDroidBrowser", "  • No recent stations in history");
                        android.util.Log.i("RadioDroidBrowser", "  • No favorites available");
                        android.util.Log.i("RadioDroidBrowser", "  • Mini-player 2nd pane will be empty");
                        android.util.Log.i("RadioDroidBrowser", "  • Recommendation: Play some stations to build history");
                    }
                }
                
                android.util.Log.i("RadioDroidBrowser", "");
                android.util.Log.i("RadioDroidBrowser", "🎯 MINI-PLAYER 2ND PANE RESULT:");
                android.util.Log.i("RadioDroidBrowser", "  • Content source: " + (recentStations != null && !recentStations.isEmpty() ? "Recent stations" : "Favorites fallback"));
                android.util.Log.i("RadioDroidBrowser", "  • Items to display: " + (stations != null ? stations.size() : 0));
                android.util.Log.i("RadioDroidBrowser", "  • Display format: LIST (optimized for mini-player)");
                android.util.Log.i("RadioDroidBrowser", "  • Icons: Will be loaded with rounded corners");
                android.util.Log.i("RadioDroidBrowser", "  • User action: Swipe left on mini-player to see these suggestions");
                android.util.Log.i("RadioDroidBrowser", "=== END MEDIA_ID_SUGGESTED CASE ===");
                android.util.Log.i("RadioDroidBrowser", "🎵🎵🎵 MINI-PLAYER 2ND PANE PROCESSING COMPLETE 🎵🎵🎵");
                android.util.Log.i("RadioDroidBrowser", "");
                break;
            }
        }

        if (stations != null && !stations.isEmpty()) {
            android.util.Log.i("RadioDroidBrowser", "=== PROCESSING STATIONS ===");
            android.util.Log.i("RadioDroidBrowser", "Parent ID: " + parentId + ", Station count: " + stations.size());
            stationIdToStation.clear();
            for (DataRadioStation station : stations) {
                stationIdToStation.put(station.StationUuid, station);
            }
            result.detach();
            
            if (parentId.equals(MEDIA_ID_SUGGESTED)) {
                android.util.Log.i("RadioDroidBrowser", "");
                android.util.Log.i("RadioDroidBrowser", "🎵 MINI-PLAYER 2ND PANE - FINAL PROCESSING:");
                android.util.Log.i("RadioDroidBrowser", "  • Starting RetrieveStationsIconAndSendResult for " + stations.size() + " mini-player suggestions");
                android.util.Log.i("RadioDroidBrowser", "  • Icons will be loaded asynchronously with rounded corners");
                android.util.Log.i("RadioDroidBrowser", "  • LIST format will be applied for mini-player optimization");
                android.util.Log.i("RadioDroidBrowser", "  • Result will be sent to Android Auto for mini-player 2nd pane display");
                android.util.Log.i("RadioDroidBrowser", "");
            } else {
                android.util.Log.i("RadioDroidBrowser", "Starting RetrieveStationsIconAndSendResult for " + stations.size() + " stations");
            }
            
            new RetrieveStationsIconAndSendResult(result, stations, radioDroidApp, parentId).execute();
        } else {
            android.util.Log.i("RadioDroidBrowser", "=== NO STATIONS FOUND ===");
            
            if (parentId.equals(MEDIA_ID_SUGGESTED)) {
                android.util.Log.i("RadioDroidBrowser", "");
                android.util.Log.i("RadioDroidBrowser", "❌ MINI-PLAYER 2ND PANE - NO CONTENT:");
                android.util.Log.i("RadioDroidBrowser", "  • No stations available for mini-player suggestions");
                android.util.Log.i("RadioDroidBrowser", "  • Mini-player 2nd pane will be empty");
                android.util.Log.i("RadioDroidBrowser", "  • User will see empty suggestions when swiping left");
                android.util.Log.i("RadioDroidBrowser", "  • Recommendation: Play some stations to build recent history");
                android.util.Log.i("RadioDroidBrowser", "");
            }
            
            android.util.Log.i("RadioDroidBrowser", "Parent ID: " + parentId + ", returning empty mediaItems list");
            android.util.Log.i("RadioDroidBrowser", "MediaItems count: " + mediaItems.size());
            result.sendResult(mediaItems);
        }
        
        if (parentId.equals(MEDIA_ID_SUGGESTED)) {
            android.util.Log.i("RadioDroidBrowser", "🎵🎵🎵 MINI-PLAYER 2ND PANE REQUEST COMPLETED 🎵🎵🎵");
        }
        android.util.Log.i("RadioDroidBrowser", "=== onLoadChildren COMPLETED for " + parentId + " ===");

    }

    @Nullable
    public DataRadioStation getStationById(@NonNull String stationId) {
        return stationIdToStation.get(stationId);
    }

    private List<MediaBrowserCompat.MediaItem> createBrowsableMediaItemsForRoot(Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        
        android.util.Log.i("RadioDroidBrowser", "=== CREATING SIMPLIFIED BROWSABLE ROOT STRUCTURE ===");
        
        // Add Favorites section
        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIconUri(resourceToUri(resources, R.drawable.ic_star_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        android.util.Log.i("RadioDroidBrowser", "Added Favorites section");

        // Add History section
        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_HISTORY)
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIconUri(resourceToUri(resources, R.drawable.ic_restore_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        android.util.Log.i("RadioDroidBrowser", "Added History section");

        android.util.Log.i("RadioDroidBrowser", "=== SIMPLIFIED ROOT STRUCTURE CREATED (" + mediaItems.size() + " sections) ===");
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
     * Generates smart recommendations based on user listening patterns (UAMP-inspired approach)
     */
    private List<DataRadioStation> generateSmartRecommendations() {
        List<DataRadioStation> recommendations = new ArrayList<>();
        
        try {
            // 1. Get most played stations from history (like UAMP's first track logic)
            List<DataRadioStation> historyStations = radioDroidApp.getHistoryManager().getList();
            if (historyStations != null && !historyStations.isEmpty()) {
                // Take top 3 most recent as "most played" approximation
                int mostPlayedCount = Math.min(3, historyStations.size());
                recommendations.addAll(historyStations.subList(0, mostPlayedCount));
                android.util.Log.i("RadioDroidBrowser", "Added " + mostPlayedCount + " most played stations to recommendations");
            }
            
            // 2. Add favorites that aren't already in recommendations
            List<DataRadioStation> favorites = radioDroidApp.getFavouriteManager().getList();
            if (favorites != null && !favorites.isEmpty()) {
                for (DataRadioStation favorite : favorites) {
                    boolean alreadyAdded = false;
                    for (DataRadioStation existing : recommendations) {
                        if (existing.StationUuid.equals(favorite.StationUuid)) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded && recommendations.size() < 8) {
                        recommendations.add(favorite);
                    }
                }
                android.util.Log.i("RadioDroidBrowser", "Added favorites to recommendations, total now: " + recommendations.size());
            }
            
            // 3. Limit to 8 recommendations for optimal UX (following UAMP's pattern)
            if (recommendations.size() > 8) {
                recommendations = recommendations.subList(0, 8);
            }
            
            android.util.Log.i("RadioDroidBrowser", "Generated " + recommendations.size() + " smart recommendations");
            
        } catch (Exception e) {
            android.util.Log.w("RadioDroidBrowser", "Error generating smart recommendations: " + e.getMessage());
            // Fallback to favorites only
            List<DataRadioStation> fallbackFavorites = radioDroidApp.getFavouriteManager().getList();
            if (fallbackFavorites != null && !fallbackFavorites.isEmpty()) {
                int maxFallback = Math.min(6, fallbackFavorites.size());
                recommendations = fallbackFavorites.subList(0, maxFallback);
            }
        }
        
        return recommendations;
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
