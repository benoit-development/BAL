package org.bbt.bal.activity;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.bbt.bal.R;
import org.bbt.bal.tools.Bal;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapActivity extends AppCompatActivity implements ConnectionCallbacks, OnConnectionFailedListener {

    /**
     * Log TAG
     */
    private static final String TAG = "map_activity";
    /**
     * Default map type
     */
    private static final int DEFAULT_MAP_TYPE = GoogleMap.MAP_TYPE_NORMAL;

    /**
     * permission request
     */
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 0;

    /**
     * Paris latitude
     */
    private static final double PARIS_LATITUDE =  48.856638;

    /**
     * Paris longitude
     */
    private static final double PARIS_LONGITUDE = 2.352241;

    /**
     * Instance of the map
     */
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    /**
     * Instance of Google API Client
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * List of current markers displayed on map
     */
    Map<String, Marker> markerList = new ConcurrentHashMap<String, Marker>();

    /**
     * Instance of an AsyncTask updating
     */
    private StringRequest currentBalListRequest = null;

    /**
     * latitude at the start of the activity
     */
    private double currentLatitude = Integer.MIN_VALUE;

    /**
     * longitude à the start of the activity
     */
    private double currentLongitude = Integer.MIN_VALUE;

    /**
     * Current selected Bal
     */
    private Bal selectedBal;

    /**
     * current map type selected
     */
    private int currentMapType = DEFAULT_MAP_TYPE;

    /**
     * button to change map type
     */
    private ImageButton mapTypeButton;

    /**
     * ShareMenuItem
     */
    private MenuItem shareMenuItem;

    /**
     * Waiting for device location to go this location
     */
    private boolean waitingForMyLocation = false;

    /**
     * Geocoder used for searches
     */
    private Geocoder gc;

    /**
     * Adapter for search suggestions
     */
    private CursorAdapter searchAdapter;

    /**
     * Asynctask making search
     */
    private AsyncTask<String, Void, Cursor> searchTask;

    /**
     * Currently displaed suggestions
     */
    private MatrixCursor suggestions;

    /**
     * Menu item to delete de Bal (only for local bal)
     */
    private MenuItem deleteMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        gc = new Geocoder(this);
        setUpMapIfNeeded();

        if (savedInstanceState != null) {
            selectedBal = (Bal) savedInstanceState.getParcelable("selectedBal");
            currentLatitude = savedInstanceState.getDouble("currentLatitude", Integer.MIN_VALUE);
            currentLongitude = savedInstanceState.getDouble("currentLongitude", Integer.MIN_VALUE);
            currentMapType = savedInstanceState.getInt("currentMapType", DEFAULT_MAP_TYPE);

            if ((currentLatitude != Integer.MIN_VALUE) && (currentLongitude != Integer.MIN_VALUE)) {
                updateBalMarkers(currentLatitude, currentLongitude);
            }
        }

        buildGoogleApiClient();

        mapTypeButton = (ImageButton) findViewById(R.id.map_type);
        mapTypeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMap != null) {
                    Log.d(TAG, "Toggling map type");
                    toggleMapType();
                }
            }
        });

        searchAdapter = new SimpleCursorAdapter(this,
                R.layout.search_suggestion,
                null,
                new String[] {"label1", "label2"},
                new int[] {android.R.id.text1, android.R.id.text2},
                CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setUpMapIfNeeded();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
        updateMapType();

        checkPermission(true);
        updateBalMarkers();
    }

    /**
     * Check/ask location permission if not active
     *
     * @return true if permission requested
     */
    private boolean checkPermission(boolean testShouldShowRequestPermissionRationale) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "Location permission not granted");

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) && testShouldShowRequestPermissionRationale) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.d(TAG, "We can't rationally ask for permission");

            } else {

                // No explanation needed, we can request the permission.
                Log.d(TAG, "Asking for permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.

                return true;
            }
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    // go to current device location
                    Log.d(TAG, "Permission has been granted");
                    waitingForMyLocation = true;


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.d(TAG, "Permission not granted :(");

                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if ((mGoogleApiClient != null) && (mGoogleApiClient.isConnected())) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_map, menu);
        shareMenuItem = menu.findItem(R.id.action_share);
        deleteMenuItem = menu.findItem(R.id.action_delete);
        // display only if a bal is selected
        shareMenuItem.setVisible(selectedBal != null);
        deleteMenuItem.setVisible((selectedBal != null) && (selectedBal.isLocal()));

        SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView search = (SearchView) menu.findItem(R.id.search).getActionView();
        search.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
        search.setSuggestionsAdapter(searchAdapter);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() >= 3) {
                    Log.i(TAG, "search request : " + newText);
                    requestSuggestion(newText);
                }
                return false;
            }
        });
        search.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                goToSelectedPosition(position);
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                goToSelectedPosition(position);
                return false;
            }

            /**
             * Move map to the selected position
             * @param position
             */
            private void goToSelectedPosition(int position) {
                Log.d(TAG, "Go to selected suggestion : " + position);
                if (suggestions.getCount() > position) {
                    suggestions.moveToPosition(position);
                    moveToLocation(suggestions.getDouble(suggestions.getColumnIndex("latitude")), suggestions.getDouble(suggestions.getColumnIndex("longitude")));
                }
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Request a new search
     *
     * @param newText
     */
    private void requestSuggestion(String newText) {

        // cancel potential search in progress
        if (searchTask != null) {
            searchTask.cancel(true);
        }

        searchTask = new AsyncTask<String, Void, Cursor>() {

            @Override
            protected Cursor doInBackground(String... params) {
                MatrixCursor c = new MatrixCursor(new String[]{ BaseColumns._ID, "label1", "label2", "latitude", "longitude" });
                List<Address> list = null;
                try {
                    list = gc.getFromLocationName(params[0], 10);
                    Log.i(TAG, list.size() + " result(s) found : " + params[0]);
                    String result = "";
                    int i=0;
                    for (Address address : list) {
                        int maxIndex = address.getMaxAddressLineIndex();
                        if (address.hasLongitude() && address.hasLatitude() && (maxIndex > -1)) {
                            String label1 = "";
                            String label2 = "";
                            for (int j=0; j<=maxIndex; j++) {
                                if (j == 0) {
                                    label1 += address.getAddressLine(j);
                                } else {
                                    label2 += address.getAddressLine(j);
                                    if (j < maxIndex) {
                                        label2 += "\n";
                                    }
                                }
                            }
                            c.addRow(new Object[] {i++, label1, label2, address.getLatitude(), address.getLongitude()});
                            suggestions = c;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return c;
            }

            @Override
            protected void onPostExecute(Cursor cursor) {
                super.onPostExecute(cursor);
                searchAdapter.changeCursor(cursor);
            }
        };

        searchTask.execute(newText);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                Log.d(TAG, "Sharing bal : " + selectedBal.toString());
                // creating intent
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                // define map type
                String mapType = "m";
                if (mMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) {
                    mapType = "h";
                }
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " : " + selectedBal.getCompleteAddress());
                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_bal_text, selectedBal.getCompleteAddress(), String.format(Locale.US, "%f", selectedBal.latitude), String.format(Locale.US, "%f", selectedBal.longitude), ((int) mMap.getCameraPosition().zoom), mapType));
                sendIntent.setType("text/html");
                startActivity(sendIntent);
                return true;
            case R.id.action_delete:
                if (selectedBal != null) {
                    Bal.deleteBalFromSharedPreferences(this, selectedBal);
                    markerList.get(selectedBal.id).remove();
                    markerList.remove(selectedBal.id);
                    selectedBal = null;
                    shareMenuItem.setVisible(false);
                    deleteMenuItem.setVisible(false);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
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
     * toggle map type between normal and hybrid
     */
    private void toggleMapType() {
        switch (currentMapType) {
            case GoogleMap.MAP_TYPE_HYBRID:
                Log.d(TAG, "new map type is normal");
                currentMapType = GoogleMap.MAP_TYPE_NORMAL;
                break;
            default:
                Log.d(TAG, "new map type is hybrid");
                currentMapType = GoogleMap.MAP_TYPE_HYBRID;
                break;
        }
        updateMapType();
    }

    /**
     * update map and image of the map type button
     */
    private void updateMapType() {
        Log.d(TAG, "Updating map type");
        switch (currentMapType) {
            case GoogleMap.MAP_TYPE_HYBRID:
                mapTypeButton.setImageResource(R.drawable.map_type_plan);
                break;
            default:
                mapTypeButton.setImageResource(R.drawable.map_type_hybrid);
                break;
        }
        if (mMap != null) {
            mMap.setMapType(currentMapType);
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {

            double previousLatitude = 0;
            double previousLongitude = 0;

            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                currentLatitude = cameraPosition.target.latitude;
                currentLongitude = cameraPosition.target.longitude;
                if ((currentLatitude != previousLatitude) && (currentLongitude != previousLongitude)) {
                    previousLatitude = currentLatitude;
                    previousLongitude = currentLongitude;
                    updateBalMarkers(currentLatitude, currentLongitude);
                }
            }
        });
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                return checkPermission(false);
            }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Log.d(TAG, "Click on marker : " + marker.getPosition().latitude + ", " + marker.getPosition().longitude);
                updateBalMarkers(marker.getPosition().latitude, marker.getPosition().longitude);
                selectedBal = new Bal(marker.getTitle());
                shareMenuItem.setVisible(true);
                deleteMenuItem.setVisible(selectedBal.isLocal());
                return false;
            }
        });
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                Log.d(TAG, "Request add on this location : " + latLng.latitude + ", " + latLng.longitude);

                // start activity to request an add
                Intent intent = new Intent(MapActivity.this, AddBalActivity.class);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_NEW_LOCATION, latLng);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_CAMERA_LAT_LNG, mMap.getCameraPosition().target);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_MAP_TYPE, currentMapType);
                startActivity(intent);
            }
        });
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                shareMenuItem.setVisible(false);
                deleteMenuItem.setVisible(false);
                selectedBal = null;
            }
        });
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                LayoutInflater inflater = (LayoutInflater) MapActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                View view = inflater.inflate(R.layout.marker_popup, null);
                Bal bal = new Bal(marker.getTitle());
                ((TextView) view.findViewById(android.R.id.text1)).setText(bal.getAddress1());
                ((TextView) view.findViewById(android.R.id.text2)).setText(bal.getAddress2());

                return view;
            }
        });
        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                if ((location != null) && waitingForMyLocation) {
                    Log.d(TAG, "Device location updated");
                    moveToMyLocation();
                    waitingForMyLocation = false;
                }
            }
        });
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            public double oldLatitude;
            public double oldLongitude;

            @Override
            public void onMarkerDragStart(Marker marker) {
                oldLatitude = marker.getPosition().latitude;
                oldLongitude = marker.getPosition().longitude;
                Log.d(TAG, "drag start : " + marker.getTitle());
            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(final Marker marker) {

                final double newLatitude = marker.getPosition().latitude;
                final double newLongitude = marker.getPosition().longitude;
                Log.d(TAG, "drag end : " + newLatitude + ", " + newLongitude);

                Bal bal = new Bal(marker.getTitle());

                // update mailbox location
                marker.remove();
                markerList.remove(bal.id);

                // start activity to request an update
                Intent intent = new Intent(MapActivity.this, UpdateBalActivity.class);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_BAL, bal);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_NEW_LOCATION, new LatLng(newLatitude, newLongitude));
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_CAMERA_ZOOM, (int) mMap.getCameraPosition().zoom);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_CAMERA_LAT_LNG, mMap.getCameraPosition().target);
                intent.putExtra(UpdateBalActivity.INTENT_EXTRA_MAP_TYPE, currentMapType);
                startActivity(intent);
            }
        });
    }

    /**
     * Create Google API instance
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /**
     * update balList from current position
     */
    private void updateBalMarkers() {
        updateBalMarkers(mMap.getCameraPosition().target.latitude, mMap.getCameraPosition().target.longitude);
    }

    /**
     * update bal list depending on the current map location
     *
     * @param latitude
     * @param longitude
     */
    private void updateBalMarkers(final double latitude, final double longitude) {

        Log.d(TAG, "Request for these coordinates : " + latitude + ", " + longitude);

        // check if a task is already running and stop it
        if (currentBalListRequest != null) {
            currentBalListRequest.cancel();
        }

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = getString(R.string.server_url) + "bal.php?latitude=" + latitude + "&longitude=" + longitude;

        // Request a string response from the provided URL.
        currentBalListRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Bal list has been received
                        Log.d(TAG, "Response received for : " + latitude + ", " + longitude);
                        Map<String, Bal> balList = Bal.parseJsonBalList(response);

                        // add local bal
                        for (Bal bal : Bal.getUserBalList(MapActivity.this)) {
                            balList.put(bal.id, bal);
                        }

                        if (balList != null) {

                            // delete markers that should not be displayed
                            Iterator<Map.Entry<String, Marker>> itMarker = markerList.entrySet().iterator();

                            while (itMarker.hasNext()) {
                                Map.Entry<String, Marker> pair = itMarker.next();
                                String id = pair.getKey();
                                Marker marker = pair.getValue();

                                if (!balList.containsKey(id)) {
                                    // Log.d(TAG, "removing " + id);
                                    marker.remove();
                                    markerList.remove(id);
                                }
                            }

                            // add new markers from new list
                            Iterator<Map.Entry<String, Bal>> itBal = balList.entrySet().iterator();

                            while (itBal.hasNext()) {
                                Map.Entry<String, Bal> pair = itBal.next();
                                String id = pair.getKey();
                                Bal bal = Bal.getBalWithLocalLocation(MapActivity.this, pair.getValue());

                                if (!markerList.containsKey(id)) {

                                    // Log.d(TAG, "adding " + id);
                                    float iconColor = BitmapDescriptorFactory.HUE_YELLOW;
                                    if (bal.isLocal()) {
                                        iconColor = BitmapDescriptorFactory.HUE_ORANGE;
                                    }
                                    Marker marker = mMap.addMarker(new MarkerOptions()
                                            .position(new LatLng(bal.latitude, bal.longitude))
                                            .title(bal.toString())
                                            .draggable(true)
                                            .icon(BitmapDescriptorFactory.defaultMarker(iconColor)));
                                    markerList.put(id, marker);

                                    if ((selectedBal != null) && (id.equals(selectedBal.id))) {
                                        marker.showInfoWindow();
                                    }
                                }
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // error while retrieving bal list
                Log.d(TAG, "Error receiving response for : " + latitude + ", " + longitude + " : " + error.getMessage());
                error.printStackTrace();
                Toast.makeText(MapActivity.this, R.string.error_retrieving_bal_list, Toast.LENGTH_LONG).show();
            }
        });
        // Add the request to the RequestQueue.
        queue.add(currentBalListRequest);

    }

    @Override
    public void onConnected(Bundle bundle) {

        // change location to current device position only if no location has been choosen
        moveToMyLocation();
    }

    /**
     * Move camera to device location and update displayed bal
     */
    private void moveToMyLocation() {
        if ((currentLatitude == Integer.MIN_VALUE) || (currentLongitude == Integer.MIN_VALUE)) {
            Log.i(TAG, "searching for current device location");
            Location location = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            if (location != null) {
                Log.d(TAG, "Device location found");
                moveToLocation(location.getLatitude(), location.getLongitude());
            } else {
                Log.d(TAG, "Device location not found :(");
                moveToLocation(PARIS_LATITUDE, PARIS_LONGITUDE);
            }
        }
    }

    /**
     * Move camera to a location and update displayed bal
     * @param latitude
     * @param longitude
     */
    private void moveToLocation(double latitude, double longitude) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15));
        updateBalMarkers(latitude, longitude);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services connection suspended.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Location services connection failed.");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("selectedBal", selectedBal);
        outState.putDouble("currentLatitude", mMap.getCameraPosition().target.latitude);
        outState.putDouble("currentLongitude", mMap.getCameraPosition().target.longitude);
        outState.putInt("currentMapType", currentMapType);
    }
}
