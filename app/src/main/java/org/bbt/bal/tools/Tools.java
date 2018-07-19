package org.bbt.bal.tools;

import android.content.SharedPreferences;
import android.util.Log;

import org.bbt.bal.BALApplication;
import org.bbt.bal.R;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

/**
 * Class grouping useful technical methods
 */
public class Tools {


    /**
     * Tag for log
     */
    private static final String TAG = "Tools";

    /**
     * Shared Preferences Key for current map type selected by user
     */
    private static final String CURRENT_MAP_TYPE_INDEX_KEY = "currentMapTypeIndex";

    /**
     * Default Map Style
     */
    private static final int DEFAULT_MAP_TYPE_INDEX = 0;

    /**
     * List of map type that user can choose
     */
    private static final ITileSource[] mapTypeList = new ITileSource[]{
            TileSourceFactory.MAPNIK,
            new MapBoxTileSource(),
            TileSourceFactory.USGS_TOPO
    };

    /**
     * {@link SharedPreferences} instance to store data
     */
    private static final SharedPreferences sharedPref = BALApplication.applicationInstance.getSharedPreferences(BALApplication.applicationInstance.getPackageName(), BALApplication.MODE_PRIVATE);

    /**
     * get map type selected by user in {@link android.content.SharedPreferences}
     *
     * @return current selected map type
     */
    public static int getCurrentMapType() {
        int mapTypeIndex = sharedPref.getInt(CURRENT_MAP_TYPE_INDEX_KEY, DEFAULT_MAP_TYPE_INDEX);
        Log.d(TAG, "Getting map type : " + mapTypeIndex);
        return mapTypeIndex;
    }

    /**
     * set last map type selected by user
     *
     * @param newMapTypeIndex new map type
     */
    public static void setCurrentMapType(int newMapTypeIndex) {
        Log.i(TAG, "setting map type : " + newMapTypeIndex);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(CURRENT_MAP_TYPE_INDEX_KEY, newMapTypeIndex);
        editor.apply();
    }

    /**
     * get current map type to use in the map
     *
     * @return current selected map type {@link ITileSource}
     */
    public static ITileSource getCurrentMapTypeTileSource() {

        ITileSource tileSource = mapTypeList[getCurrentMapType()];

        //setup mapbox key and id
        if (tileSource instanceof MapBoxTileSource) {
            ((MapBoxTileSource) tileSource).setAccessToken(BALApplication.applicationInstance.getString(R.string.mapbox_key));
            ((MapBoxTileSource) tileSource).setMapboxMapid(BALApplication.applicationInstance.getString(R.string.mapbox_id));
        }

        return tileSource;
    }
}
