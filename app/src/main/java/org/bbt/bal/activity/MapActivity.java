package org.bbt.bal.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.bbt.bal.R;
import org.bbt.bal.tools.Bal;
import org.bbt.bal.tools.Tools;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapActivity extends AppCompatActivity {

    /**
     * Log TAG
     */
    private static final String TAG = "map_activity";

    /**
     * permission request
     */
    private static final int MY_PERMISSIONS_REQUEST = 0;

    /**
     * Paris latitude
     */
    private static final double PARIS_LATITUDE = 48.856638;

    /**
     * Paris longitude
     */
    private static final double PARIS_LONGITUDE = 2.352241;

    /**
     * Default zoom level
     */
    private static final double DEFAULT_ZOOM = 15;

    /**
     * Instance of an AsyncTask updating
     */
    private StringRequest currentBalListRequest = null;

    /**
     * latitude at the start of the activity
     */
    private double currentLatitude = PARIS_LATITUDE;

    /**
     * longitude à the start of the activity
     */
    private double currentLongitude = PARIS_LONGITUDE;

    /**
     * Current selected Bal
     */
    private String selectedBalId = "";

    /**
     * View displaying map
     */
    private MapView mapView;

    /**
     * current zoom level on map view
     */
    private double currentZoom = DEFAULT_ZOOM;

    /**
     * Marker of the current location device
     */
    private Marker myLocationMarker;

    /**
     * Boolean defining if system should change camera position to current device location
     */
    private boolean shouldAppMoveCameraPosition = true;

    /**
     * Client to get last known location
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Current device location
     */
    private Location currentDeviceLocation;

    /**
     * {@link MapView} controller
     */
    private IMapController mapController;

    /**
     * default inactivity before reloading bal markers
     */
    private static final int DEFAULT_INACTIVITY_DELAY_IN_MILLISECS = 200;

    /**
     * Marker list on map
     */
    private final ConcurrentHashMap<String, Marker> markerList = new ConcurrentHashMap<>();

    /**
     * Current bal list identified with bal ID
     */
    private Map<String, Bal> balList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            selectedBalId = savedInstanceState.getString("selectedBalId", "");
            currentLatitude = savedInstanceState.getDouble("currentLatitude", PARIS_LATITUDE);
            currentLongitude = savedInstanceState.getDouble("currentLongitude", PARIS_LONGITUDE);
            currentDeviceLocation = savedInstanceState.getParcelable("currentDeviceLocation");
            currentZoom = savedInstanceState.getDouble("currentZoom", DEFAULT_ZOOM);
            shouldAppMoveCameraPosition = savedInstanceState.getBoolean("shouldAppMoveCameraPosition", true);
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //handle permissions first, before map is created. not depicted here
        if (checkPermission()) {
            setUpMap();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
            markerList.clear();
            moveToMyLocation(false);
            updateMapStyle();
            updateBalMarkers(currentLatitude, currentLongitude);
            if (currentDeviceLocation != null) {
                updateCurrentPositionMarker(currentDeviceLocation);
            }
        }
    }

    /**
     * Move camera to device location and update displayed bal.
     * Moving to device location can be forced for example when user click on "go to my location" button.
     *
     * @param force force moving
     */
    private void moveToMyLocation(boolean force) {

        if ((currentDeviceLocation != null) && (mapView != null)) {
            Log.d(TAG, "current device location available");

            if (shouldAppMoveCameraPosition || force) {
                moveToLocation(currentDeviceLocation.getLatitude(), currentDeviceLocation.getLongitude());
                shouldAppMoveCameraPosition = false;
            }

        } else if (mapView != null) {
            Log.d(TAG, "No current device location available. Trying to get one from last known location");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                checkPermission();
                return;
            }
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, (Location location) -> {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            Log.d(TAG, "Last known location found : " + location.getLatitude() + ", " + location.getLongitude());
                            updateCurrentPositionMarker(location);
                            if (shouldAppMoveCameraPosition || force) {
                                moveToMyLocation(false);
                                shouldAppMoveCameraPosition = false;
                            }
                        }
                    });
        }
    }

    /**
     * Update device current position marker on map
     *
     * @param location new device location
     */
    private void updateCurrentPositionMarker(Location location) {
        currentDeviceLocation = location;
        if (myLocationMarker != null) {
            myLocationMarker.setPosition(new GeoPoint(currentDeviceLocation));
        }
    }


    /**
     * Check/ask location permission if not active
     */
    private boolean checkPermission() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {

            Log.d(TAG, "Location permission not granted");

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.d(TAG, "Can't rationally ask for permissions");
                Toast.makeText(this, R.string.permission_not_granted_message, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // No explanation needed, we can request the permission.
                Log.d(TAG, "Asking for permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST);
            }
            return false;

        } else {
            // Permission granted
            locationPermissionGranted();
            setUpMap();
            return true;
        }
    }

    /**
     * Add device location listener when Location permission is granted
     */
    private void locationPermissionGranted() {
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Log.d(TAG, "Location Manager is null. No possibility to find device location");
            return;
        }

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                Log.d(TAG, "Device location changed : " + location.getLatitude() + "/" + location.getLongitude());
                moveToMyLocation(false);
                updateCurrentPositionMarker(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkPermission();
            return;
        }
        // Register the listener with the Location Manager to receive location update
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // go to current device location
                    Log.d(TAG, "Permission has been granted");
                    setUpMap();
                    locationPermissionGranted();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.d(TAG, "Permission not granted :(");
                    Toast.makeText(this, R.string.permission_not_granted_message, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_my_location:
                Log.d(TAG, "My Location requested");
                moveToMyLocation(true);
                return true;
            // activate this menu item when osmdroid will have satellite map in France
            case R.id.action_map_layer:
                Log.d(TAG, "Display AlertDialog to choose map type");

                DialogFragment newFragment = new MapTypeDialogFragment();
                newFragment.show(getSupportFragmentManager(), "missiles");

                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * update map and image of the map style button
     */
    private void updateMapStyle() {
        mapView.setTileSource(Tools.getCurrentMapTypeTileSource());
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera.
     * <p/>
     * This should only be called once and when we are sure that {@link #mapView} is not null.
     */
    private void setUpMap() {

        //load/initialize the osmdroid configuration, this can be done
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        Configuration.getInstance().setOsmdroidBasePath(new File(Environment.getExternalStorageDirectory(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(Environment.getExternalStorageDirectory(), "osmdroid/tiles"));

        // inflate activity layout
        setContentView(R.layout.activity_map);

        // setup map
        mapView = findViewById(R.id.mapView);
        updateMapStyle();
        mapView.setBuiltInZoomControls(false);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);

        // init controls
        mapController = mapView.getController();
        mapController.setZoom(currentZoom);
        mapController.setCenter(new GeoPoint(currentLatitude, currentLongitude));

        // listener to update displayed mailboxes
        mapView.addMapListener(new DelayedMapListener(new MapListener() {

            double previousLatitude = 0;
            double previousLongitude = 0;

            @Override
            public boolean onScroll(ScrollEvent event) {

                shouldAppMoveCameraPosition = false;

                currentLatitude = mapView.getMapCenter().getLatitude();
                currentLongitude = mapView.getMapCenter().getLongitude();

                Log.d(TAG, "Map position moved to : " + currentLatitude + "/" + currentLongitude);
                if ((currentLatitude != previousLatitude) && (currentLongitude != previousLongitude)) {
                    previousLatitude = currentLatitude;
                    previousLongitude = currentLongitude;
                    updateBalMarkers(currentLatitude, currentLongitude);
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                currentZoom = mapView.getZoomLevelDouble();
                return false;
            }
        }, DEFAULT_INACTIVITY_DELAY_IN_MILLISECS));

        // "my location" marker
        myLocationMarker = new Marker(mapView);
        myLocationMarker.setIcon(getDrawable(R.drawable.ic_marker_my_location));
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        myLocationMarker.setInfoWindow(null);
        mapView.getOverlays().add(myLocationMarker);
    }

    /**
     * update bal list depending on the current map location
     *
     * @param latitude  latitude for search
     * @param longitude longitude for search
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
        currentBalListRequest = new StringRequest(Request.Method.GET, url, (String response) -> {
            // Bal list has been received
            Log.d(TAG, "Response received for : " + latitude + ", " + longitude);
            balList = Bal.parseJsonBalList(response);

            if (balList != null) {

                // delete markers that should not be displayed
                for (Map.Entry<String, Marker> pair : markerList.entrySet()) {
                    String id = pair.getKey();
                    Marker marker = pair.getValue();

                    if (!balList.containsKey(id)) {
                        Log.d(TAG, "removing " + id);
                        mapView.getOverlays().remove(marker);
                        if (selectedBalId.equals(marker.getId())) {
                            marker.closeInfoWindow();
                        }
                        markerList.remove(id);
                    }
                }

                // add new markers from new list
                for (Map.Entry<String, Bal> pair : balList.entrySet()) {
                    String id = pair.getKey();
                    Bal bal = pair.getValue();

                    Marker marker;
                    if (!markerList.containsKey(id)) {
                        Log.d(TAG, "adding " + id);

                        // create marker
                        marker = new Marker(mapView);
                        marker.setPosition(new GeoPoint(bal.latitude, bal.longitude));
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        marker.setIcon(getDrawable(R.drawable.ic_marker_bal_location));
                        marker.setTextLabelFontSize(30);
                        marker.setTitle(bal.getAddress1() + "\n" + bal.getAddress2());
                        marker.setId(bal.id);
                        marker.setInfoWindow(new MyInfoWindow(mapView));
                        marker.setOnMarkerClickListener((Marker markerClicked, MapView mapView) -> {
                            Log.d(TAG, "new selected bal : " + markerClicked.getId());
                            selectedBalId = markerClicked.getId();
                            markerClicked.showInfoWindow();
                            moveToLocation(markerClicked.getPosition().getLatitude(), markerClicked.getPosition().getLongitude());
                            return true;
                        });
                        mapView.getOverlays().add(marker);
                        markerList.put(id, marker);

                        if (selectedBalId.equals(marker.getId())) {
                            Log.d(TAG, "selected bal found : " + selectedBalId);
                            marker.showInfoWindow();
                        }

                    } else {
                        Log.d(TAG, "not adding " + id);
                    }

                }

                mapView.invalidate();
            }
        }, (VolleyError error) -> {
            // error while retrieving bal list
            Log.d(TAG, "Error receiving response for : " + latitude + ", " + longitude + " : " + error.getMessage());
            error.printStackTrace();
            Toast.makeText(MapActivity.this, R.string.error_retrieving_bal_list, Toast.LENGTH_LONG).show();

        });
        // Add the request to the RequestQueue.
        queue.add(currentBalListRequest);

    }

    /**
     * Move camera to a location and update displayed bal
     *
     * @param latitude  latitude to move to
     * @param longitude longitude to move to
     */
    private void moveToLocation(double latitude, double longitude) {
        mapController.animateTo(new GeoPoint(latitude, longitude));
        updateBalMarkers(latitude, longitude);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedBalId", selectedBalId);
        outState.putDouble("currentLatitude", currentLatitude);
        outState.putDouble("currentLongitude", currentLongitude);
        outState.putParcelable("currentDeviceLocation", currentDeviceLocation);
        outState.putDouble("currentZoom", currentZoom);
        outState.putBoolean("shouldAppMoveCameraPosition", shouldAppMoveCameraPosition);
    }

    /**
     * Custom {@link InfoWindow} view
     */
    private class MyInfoWindow extends InfoWindow {

        /**
         * Text displaying address
         */
        private final TextView textView;

        /**
         * Button to navigate to this mailbox
         */
        private final ImageView navigateImage;

        /**
         * Button to share mailbox
         */
        private final ImageView shareImage;

        MyInfoWindow(MapView mapView) {
            super(R.layout.marker_popup, mapView);

            textView = mView.findViewById(R.id.text);
            navigateImage = mView.findViewById(R.id.navigate);
            shareImage = mView.findViewById(R.id.share);
        }

        @Override
        public void onOpen(Object item) {
            // close every opened InfoWindow (only one can be opened)
            closeAllInfoWindowsOn(getMapView());

            if (item instanceof Marker) {
                Marker marker = (Marker) item;
                Log.d(TAG, "Displaying InfoWindow of " + marker.getId());

                textView.setText(marker.getTitle());
                navigateImage.setOnClickListener((View v) -> {
                    Log.d(TAG, "Route to this marker requested");
                    startNavigation(marker.getPosition().getLatitude(), marker.getPosition().getLongitude());
                });
                shareImage.setOnClickListener((View v) -> {
                    Log.d(TAG, "Sharing of this marker requested");
                    shareBal(balList.get(marker.getId()));
                });
            } else {
                Log.e(TAG, "No marker found (should not happen)");
            }
        }

        @Override
        public void onClose() {

        }
    }

    /**
     * Star navigation to the desired mailbox
     *
     * @param latitude  mailbox latitude
     * @param longitude mailbox longitude
     */
    private void startNavigation(double latitude, double longitude) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + String.valueOf(latitude) + "," + String.valueOf(longitude));
        Log.d(TAG, gmmIntentUri.toString());
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        }
    }

    /**
     * Share a {@link Bal} with other applications
     *
     * @param bal selected bal to be shared
     */
    private void shareBal(Bal bal) {
        if (bal != null) {
            String uri = "http://maps.google.com/maps?q=" + bal.latitude + "," + bal.longitude;

            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            String ShareSub = getString(R.string.mailbox) + " - " + bal.getCompleteAddress();
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, ShareSub);
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, uri);
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
        } else {
            Log.e(TAG, "No bal found for sharing (should not happen)");
        }
    }

    /**
     * {@link DialogFragment} offering map type choice to user
     */
    public static class MapTypeDialogFragment extends DialogFragment {

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            Log.d(TAG, "Creating dialog to choose map type");

            if (getActivity() instanceof MapActivity) {
                MapActivity activity = (MapActivity) getActivity();
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(R.layout.map_type_custom_dialog, null);
                View roadImage = view.findViewById(R.id.road_image);
                View satelliteImage = view.findViewById(R.id.satellite_image);

                //set actions
                roadImage.setOnClickListener((View v) -> {
                    Tools.setCurrentMapType(0);
                    activity.updateMapStyle();
                    dismiss();
                });
                satelliteImage.setOnClickListener((View v) -> {
                    Tools.setCurrentMapType(1);
                    activity.updateMapStyle();
                    dismiss();
                });
                if (Tools.getCurrentMapType() == 0) {
                    // road currently selected
                    satelliteImage.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    // satellite currently selected
                    roadImage.setBackgroundColor(Color.TRANSPARENT);
                }

                builder.setTitle(R.string.map_type)
                        .setView(view);
                // Create the AlertDialog object and return it
                return builder.create();
            } else {
                Log.e(TAG, "No MapActivity found (should not happen)");
                //noinspection ConstantConditions
                return null;
            }
        }
    }
}
