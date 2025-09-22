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
            return Utils.getRealStationLink(httpClient, context.getApplicationContext(), station.StationUuid);
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
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        super.onPostExecute(result);
    }
}
