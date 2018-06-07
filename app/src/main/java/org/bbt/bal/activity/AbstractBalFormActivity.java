package org.bbt.bal.activity;

import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.bbt.bal.R;
import org.bbt.bal.tools.Bal;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Benoit on 25/10/2015.
 */
public abstract class AbstractBalFormActivity extends AppCompatActivity {


    /**
     * Tag for log
     */
    protected String TAG = "Update/Add activity";

    /**
     * Intent extra to send a serialized bal to update
     */
    public static final String INTENT_EXTRA_NEW_LOCATION = "org.bbt.bal.activity.UpdateActivity.newLocation";

    /**
     * Intent extra to send a serialized bal to update
     */
    public static final String INTENT_EXTRA_MAP_TYPE = "org.bbt.bal.activity.UpdateActivity.mapType";

    /**
     * Intent extra to send a serialized bal to update
     */
    public static final String INTENT_EXTRA_CAMERA_ZOOM = "org.bbt.bal.activity.UpdateActivity.cameraZoom";

    /**
     * Intent extra to send a serialized bal to update
     */
    public static final String INTENT_EXTRA_CAMERA_LAT_LNG = "org.bbt.bal.activity.UpdateActivity.cameraLatLng";

    /**
     * New location
     */
    protected LatLng newLocation;

    /**
     * street number (only for local server)
     */
    protected EditText streetNumber;

    /**
     * street name (only for local server)
     */
    protected EditText streetName;

    /**
     * postalCode (only for local server)
     */
    protected EditText postalCode;

    /**
     * town (only for local server)
     */
    protected EditText town;

    /**
     * comment associated to this location move
     */
    protected EditText comment;

    /**
     * MapView displaying bal location change
     */
    protected GoogleMap mMap;

    /**
     * MapView of this activity
     */
    protected View mapView;

    /**
     * Map type to use to display map
     */
    protected int mapType;

    /**
     * default camera zoom for first display
     */
    protected int cameraZoom;

    /**
     * default camera location
     */
    protected LatLng cameraLocation;

    /**
     * From bal
     */
    protected Marker markerNew;

    /**
     * asynctask searching for an address from location
     */
    protected AsyncTask<LatLng, Void, Bal> searchTask;

    /**
     * Geocoder instance
     */
    protected Geocoder gc;

    /**
     * Define if geocoder has already found an address
     */
    boolean addressFoundWithGeocoder = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gc = new Geocoder(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed)
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * Setup the map
     */
    protected abstract void setUpMap();

    /**
     * Launch an asynctask to find address from location
     */
    protected void requestAddressFromGeocoder() {

        // cancel potential search in progress
        if (searchTask != null) {
            searchTask.cancel(true);
        }

        searchTask = new AsyncTask<LatLng, Void, Bal>() {

            @Override
            protected Bal doInBackground(LatLng... params) {
                Bal result = null;
                List<Address> list = null;
                try {
                    list = gc.getFromLocation(newLocation.latitude, newLocation.longitude, 1);
                    Log.i(TAG, list.size() + " result found");
                    if (list.size() > 0) {
                        Address address = list.get(0);
                        String postalCode = address.getPostalCode();
                        // String countryCode = address.getCountryCode();
                        String town = address.getLocality();
                        String streetName = "";
                        String streetNumber = "";
                        int maxIndex = address.getMaxAddressLineIndex();
                        if (maxIndex > -1) {

                            streetName += address.getAddressLine(0);
                            if (streetName.equals(town)) {
                                streetName = "";
                            } else {
                                // test street number
                                Pattern p = Pattern.compile("^([0-9-]+) .+");
                                Matcher m = p.matcher(streetName);
                                if (m.matches()) {
                                    streetNumber = m.group(1);
                                    streetName = streetName.substring(streetNumber.length() + 1);
                                }
                            }

                        }
                        result = new Bal("", streetNumber, streetName, "", town, postalCode, "", 0, 0, Bal.Type.local, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return result;
            }

            @Override
            protected void onPostExecute(Bal bal) {
                if (bal != null) {
                    super.onPostExecute(bal);
                    streetNumber.setText(bal.streetNumber);
                    streetName.setText(bal.streetName);
                    postalCode.setText(bal.postalCode);
                    town.setText(bal.town);
                    addressFoundWithGeocoder = true;
                }
            }
        };

        searchTask.execute(newLocation);
    }

}
