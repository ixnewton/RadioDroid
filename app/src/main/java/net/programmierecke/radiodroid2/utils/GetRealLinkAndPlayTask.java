package net.programmierecke.radiodroid2.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.RemoteException;

import androidx.preference.PreferenceManager;

import net.programmierecke.radiodroid2.FavouriteManager;
import net.programmierecke.radiodroid2.IPlayerService;
import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.station.DataRadioStation;

import java.lang.ref.WeakReference;

import okhttp3.OkHttpClient;

public class GetRealLinkAndPlayTask extends AsyncTask<Void, Void, String> {
    private WeakReference<Context> contextRef;
    private DataRadioStation station;
    private WeakReference<IPlayerService> playerServiceRef;

    private OkHttpClient httpClient;

    public GetRealLinkAndPlayTask(Context context, DataRadioStation station, IPlayerService playerService) {
        this.contextRef = new WeakReference<>(context);
        this.station = station;
        this.playerServiceRef = new WeakReference<>(playerService);

        RadioDroidApp radioDroidApp = (RadioDroidApp) context.getApplicationContext();
        httpClient = radioDroidApp.getHttpClient();
    }

    @Override
    protected String doInBackground(Void... params) {
        Context context = contextRef.get();
        if (context != null) {
            android.util.Log.i("GetRealLinkAndPlayTask", "Resolving stream URL for station: " + station.Name + " (UUID: " + station.StationUuid + ")");
            
            // Check if this is an m3u8 stream
            boolean isM3u8Stream = station.StreamUrl != null && Utils.urlIndicatesHlsStream(station.StreamUrl);
            if (isM3u8Stream) {
                android.util.Log.i("GetRealLinkAndPlayTask", "🎵 M3U8/HLS stream detected: " + station.StreamUrl);
            }
            
            String resolvedUrl = Utils.getRealStationLink(httpClient, context.getApplicationContext(), station.StationUuid);
            
            if (resolvedUrl != null) {
                android.util.Log.i("GetRealLinkAndPlayTask", "✅ Stream URL resolved successfully: " + resolvedUrl);
                if (isM3u8Stream) {
                    android.util.Log.i("GetRealLinkAndPlayTask", "🎵 M3U8/HLS stream resolved - ready for native HLS playback");
                }
                return resolvedUrl;
            } else {
                android.util.Log.w("GetRealLinkAndPlayTask", "❌ Failed to resolve stream URL from RadioBrowser API");
                
                // For m3u8 streams, try using the original URL as fallback
                if (isM3u8Stream && station.StreamUrl != null && !station.StreamUrl.isEmpty()) {
                    android.util.Log.i("GetRealLinkAndPlayTask", "🔄 M3U8 fallback: Using original URL directly: " + station.StreamUrl);
                    return station.StreamUrl;
                }
                
                android.util.Log.e("GetRealLinkAndPlayTask", "❌ No fallback available for station: " + station.Name);
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(String result) {
        IPlayerService playerService = playerServiceRef.get();
        Context context = contextRef.get();
        
        if (result != null && playerService != null && context != null && !isCancelled()) {
            try {
                station.playableUrl = result;
                
                // Check if this is an m3u8 stream for logging
                boolean isM3u8Stream = Utils.urlIndicatesHlsStream(result);
                if (isM3u8Stream) {
                    android.util.Log.i("GetRealLinkAndPlayTask", "🎵 Starting M3U8/HLS playback for: " + station.Name);
                    android.util.Log.i("GetRealLinkAndPlayTask", "🎵 M3U8 URL: " + result);
                } else {
                    android.util.Log.i("GetRealLinkAndPlayTask", "▶️ Starting playback for: " + station.Name);
                }
                
                // Add station to history when played from Android Auto (same as PlayStationTask)
                RadioDroidApp radioDroidApp = (RadioDroidApp) context.getApplicationContext();
                radioDroidApp.getHistoryManager().add(station);
                android.util.Log.i("GetRealLinkAndPlayTask", "Added station to history: " + station.Name);
                
                // Check for auto-favorite functionality (same as PlayStationTask)
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                boolean autoFavorite = sharedPref.getBoolean("auto_favorite", false);
                
                if (autoFavorite) {
                    FavouriteManager favouriteManager = radioDroidApp.getFavouriteManager();
                    if (!favouriteManager.has(station.StationUuid)) {
                        favouriteManager.add(station);
                        android.util.Log.i("GetRealLinkAndPlayTask", "Auto-favorited station: " + station.Name);
                    }
                }
                
                playerService.SetStation(station);
                playerService.Play(false);
                
                android.util.Log.i("GetRealLinkAndPlayTask", "✅ Playback initiated successfully for: " + station.Name);
                
            } catch (RemoteException e) {
                android.util.Log.e("GetRealLinkAndPlayTask", "❌ Failed to start playback for: " + station.Name + " - " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Handle the case where URL resolution failed
            if (result == null) {
                android.util.Log.e("GetRealLinkAndPlayTask", "❌ Cannot start playback - URL resolution failed for: " + (station != null ? station.Name : "unknown station"));
            } else if (playerService == null) {
                android.util.Log.e("GetRealLinkAndPlayTask", "❌ Cannot start playback - PlayerService not available");
            } else if (context == null) {
                android.util.Log.e("GetRealLinkAndPlayTask", "❌ Cannot start playback - Context not available");
            } else if (isCancelled()) {
                android.util.Log.i("GetRealLinkAndPlayTask", "⏹️ Playback cancelled for: " + (station != null ? station.Name : "unknown station"));
            }
        }
        super.onPostExecute(result);
    }
}
