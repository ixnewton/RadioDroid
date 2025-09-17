package net.programmierecke.radiodroid2.station

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.appcompat.widget.PopupMenu
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.sizeDp
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.players.PlayStationTask
import net.programmierecke.radiodroid2.players.selector.PlayerType

object StationPopupMenu {
    fun open(view: View, context: Context, activity: FragmentActivity, station: DataRadioStation, itemAdapterStation: ItemAdapterStation): PopupMenu {
        val rootView = view.rootView
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        val play_external = sharedPref.getBoolean("play_external", false)

        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.popup_menu_station)

        val playExternalItem = popupMenu.menu.findItem(R.id.context_menu_play_in_external_player)
        val playInAppItem = popupMenu.menu.findItem(R.id.context_menu_play_in_radiodroid)

        if (play_external) {
            playExternalItem.isVisible = false
        } else {
            playInAppItem.isVisible = false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            popupMenu.menu.findItem(R.id.context_menu_create_shortcut).isVisible = false
        }

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.context_menu_play_in_radiodroid -> {
                    StationActions.playInRadioDroid(context, station)
                    true
                }
                R.id.context_menu_play_in_external_player -> {
                    Utils.playAndWarnIfMetered(context.applicationContext as RadioDroidApp, station,
                            PlayerType.EXTERNAL) { PlayStationTask.playExternal(station, context).execute() }
                    true
                }
                R.id.context_menu_visit_homepage -> {
                    StationActions.openStationHomeUrl(activity, station)
                    true
                }
                R.id.context_menu_share -> {
                    StationActions.share(context, station)
                    true
                }
                R.id.context_menu_add_alarm -> {
                    StationActions.setAsAlarm(activity, station)
                    true
                }
                R.id.context_menu_create_shortcut -> {
                    station.prepareShortcut(context, itemAdapterStation.CreatePinShortcutListener())
                    true
                }
                R.id.context_menu_delete -> {
                    StationActions.removeFromFavourites(context, rootView, station)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
        return popupMenu
    }
}