package net.programmierecke.radiodroid2.station;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.PopupMenu;

import net.programmierecke.radiodroid2.R;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.service.PlayerServiceUtil;
import net.programmierecke.radiodroid2.utils.RecyclerItemMoveAndSwipeHelper;
import net.programmierecke.radiodroid2.utils.SwipeableViewHolder;

public class ItemAdapterIconOnlyStation extends ItemAdapaterContextMenuStation implements RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    class StationViewHolder extends ItemAdapterStation.StationViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener, SwipeableViewHolder {
        PopupMenu contextMenu = null;

        StationViewHolder(View itemView) {
            super(itemView);

            viewForeground = itemView.findViewById(R.id.station_icon_foreground);
            frameLayout = itemView.findViewById(R.id.stationIconFrameLayout);

            imageViewIcon = itemView.findViewById(R.id.iconImageViewIcon);
            transparentImageView = itemView.findViewById(R.id.iconTransparentCircle);
            itemView.setOnCreateContextMenuListener(this);
        }

        public void dismissContextMenu() {
            if (contextMenu != null) {
                contextMenu.dismiss();
                contextMenu = null;
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            if (contextMenu != null)
                return;
            int pos = getAdapterPosition();
            DataRadioStation station = filteredStationsList.get(pos);
            contextMenu = StationPopupMenu.INSTANCE.open(v, getContext(), activity, station, ItemAdapterIconOnlyStation.this);
            contextMenu.setOnDismissListener(popup -> {
                dismissContextMenu();
            });
        }
    }

    public ItemAdapterIconOnlyStation(FragmentActivity fragmentActivity, int resourceId, StationsFilter.FilterType filterType) {
        super(fragmentActivity, resourceId, filterType);
    }

    @NonNull
    @Override
    public StationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(resourceId, parent, false);

        return new StationViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final ItemAdapterStation.StationViewHolder holder, int position) {
        final DataRadioStation station = filteredStationsList.get(position);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        boolean useCircularIcons = Utils.useCircularIcons(getContext());

        // Calculate dynamic icon size based on screen width and number of columns
        calculateAndSetIconSize(holder);

        if (station.hasIcon()) {
            setupIcon(useCircularIcons, holder.imageViewIcon, holder.transparentImageView);
            PlayerServiceUtil.getStationIcon(holder.imageViewIcon, station.IconUrl);
        } else {
            holder.imageViewIcon.setImageDrawable(stationImagePlaceholder);
        }

        TypedValue tv = new TypedValue();
        if (playingStationPosition == position) {
            getContext().getTheme().resolveAttribute(R.attr.colorAccentMy, tv, true);
            holder.frameLayout.setBackgroundColor(tv.data);
            holder.transparentImageView.setColorFilter(tv.data);
        } else {
            getContext().getTheme().resolveAttribute(R.attr.boxBackgroundColor, tv, true);
            holder.frameLayout.setBackgroundColor(tv.data);
        }
    }

    public void enableItemMove(RecyclerView recyclerView) {
        RecyclerItemMoveAndSwipeHelper swipeAndMoveHelper = new RecyclerItemMoveAndSwipeHelper<>(getContext(), ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0, this);
        new ItemTouchHelper(swipeAndMoveHelper).attachToRecyclerView(recyclerView);
    }

    /**
     * Calculate and set dynamic icon size based on screen width and number of columns
     */
    private void calculateAndSetIconSize(ItemAdapterStation.StationViewHolder holder) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        
        // Get number of columns based on orientation
        int orientation = getContext().getResources().getConfiguration().orientation;
        boolean isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        String prefKey = isLandscape ? "grid_columns_landscape" : "grid_columns_portrait";
        String defaultValue = isLandscape ? "7" : "4";
        String columnsStr = prefs.getString(prefKey, defaultValue);
        
        int numColumns;
        try {
            numColumns = Integer.parseInt(columnsStr);
        } catch (NumberFormatException e) {
            numColumns = Integer.parseInt(defaultValue);
        }
        
        // Calculate available width
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        
        // Calculate item width with no margins for regular layout
        int marginDp = 0;
        float density = displayMetrics.density;
        int marginPx = (int) (marginDp * density);
        int totalMargins = marginPx * 2 * numColumns; // 2 margins per item
        
        int availableWidth = screenWidth - totalMargins;
        int itemWidth = availableWidth / numColumns;
        
        // Set frame layout size (container)
        ViewGroup.LayoutParams frameParams = holder.frameLayout.getLayoutParams();
        frameParams.width = itemWidth;
        frameParams.height = itemWidth;
        holder.frameLayout.setLayoutParams(frameParams);
        
        // Set icon size (80% of container size for nice padding)
        int iconSize = (int) (itemWidth * 0.8f);
        ViewGroup.LayoutParams iconParams = holder.imageViewIcon.getLayoutParams();
        iconParams.width = iconSize;
        iconParams.height = iconSize;
        holder.imageViewIcon.setLayoutParams(iconParams);
        
        // Set transparent circle size to match icon
        ViewGroup.LayoutParams circleParams = holder.transparentImageView.getLayoutParams();
        circleParams.width = iconSize;
        circleParams.height = iconSize;
        holder.transparentImageView.setLayoutParams(circleParams);
        
        // Set margins on the root view for spacing
        ViewGroup.MarginLayoutParams rootParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (rootParams == null) {
            rootParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        rootParams.setMargins(marginPx, marginPx, marginPx, marginPx);
        holder.itemView.setLayoutParams(rootParams);
    }
}

