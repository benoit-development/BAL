package org.bbt.bal.activity;

import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.bbt.bal.R;
import org.bbt.bal.tools.Bal;

import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddBalActivity extends AbstractBalFormActivity {

    /**
     * Tag for logs
     */
    private static final String TAG = "AddBalActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_local_bal);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        newLocation = (LatLng) getIntent().getParcelableExtra(INTENT_EXTRA_NEW_LOCATION);
        cameraLocation = (LatLng) getIntent().getParcelableExtra(INTENT_EXTRA_CAMERA_LAT_LNG);
        mapType = getIntent().getIntExtra(INTENT_EXTRA_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
        cameraZoom = 17;

        // check if all information are available
        if (newLocation == null) {
            Toast.makeText(this, R.string.error_displaying_add_form, Toast.LENGTH_LONG).show();
            finish();
        }

        // set view
        setTitle(R.string.adding_new_bal);
        streetNumber = (EditText) findViewById(R.id.street_number);
        streetName = (EditText) findViewById(R.id.street_name);
        postalCode = (EditText) findViewById(R.id.postal_code);
        town = (EditText) findViewById(R.id.town);
        comment = (EditText) findViewById(R.id.comment);
        mapView = findViewById(R.id.map);

        // update activity state
        if (savedInstanceState != null) {
            streetNumber.setText(savedInstanceState.getString("street_number", ""));
            streetName.setText(savedInstanceState.getString("address", ""));
            postalCode.setText(savedInstanceState.getString("postal_code", ""));
            town.setText(savedInstanceState.getString("town", ""));
            comment.setText(savedInstanceState.getString("comment", ""));
            newLocation = (LatLng) savedInstanceState.getParcelable("newLocation");
            addressFoundWithGeocoder = savedInstanceState.getBoolean("addressFoundWithGeocoder", false);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!addressFoundWithGeocoder) {
            requestAddressFromGeocoder();
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
     * This is where we can add markers or lines, add listeners or move the camera.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    protected void setUpMap() {
        mMap.setMyLocationEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        if (cameraLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraLocation, cameraZoom));
        }
        mMap.setMapType(mapType);

        markerNew = mMap.addMarker(new MarkerOptions()
                .position(newLocation)
                .draggable(true)
                .title(getString(R.string.new_bal))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        markerNew.showInfoWindow();

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                updateCameraPosition();
            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                Log.i(TAG, "New Marker position : " + latLng.latitude + ", " + latLng.longitude);
                markerNew.setPosition(latLng);
                newLocation = latLng;
                updateCameraPosition();
                requestAddressFromGeocoder();
            }
        });

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {

            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                Log.i(TAG, "New Marker position : " + marker.getPosition().latitude + ", " + marker.getPosition().longitude);
                newLocation = marker.getPosition();
                updateCameraPosition();
                requestAddressFromGeocoder();
            }
        });
    }

    /**
     * update zoom and location
     */
    private void updateCameraPosition() {
        // move to new location
        mMap.animateCamera(CameraUpdateFactory.newLatLng(newLocation));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_update_bal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_send) {

            Log.d(TAG, "saving new bal");
            Log.d(TAG, "address : " + streetNumber.getText().toString() + " " + streetName.getText().toString() + " " + postalCode.getText().toString() + " " + town.getText().toString());
            Log.d(TAG, "location : " + newLocation.latitude + ", " + newLocation.longitude);
            Log.d(TAG, "comment : " + comment.getText().toString());

            Log.d(TAG, "server add requested");
            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(this);
            try {
                String url = getString(R.string.server_url) + "bal_request_add.php?"
                        + "&street_number=" + URLEncoder.encode(streetNumber.getText().toString(), "UTF-8")
                        + "&street_name=" + URLEncoder.encode(streetName.getText().toString(), "UTF-8")
                        + "&postal_code=" + URLEncoder.encode(postalCode.getText().toString(), "UTF-8")
                        + "&town=" + URLEncoder.encode(town.getText().toString(), "UTF-8")
                        + "&latitude=" + newLocation.latitude
                        + "&longitude=" + newLocation.longitude
                        + "&comment=" + URLEncoder.encode(comment.getText().toString(), "UTF-8");

                // Request a string response from the provided URL.
                StringRequest updateRequest = new StringRequest(Request.Method.GET, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.d(TAG, "request sent");
                                Toast.makeText(AddBalActivity.this, R.string.request_sent, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // error while retrieving bal list
                                Log.d(TAG, "Error sending request");
                                error.printStackTrace();
                                // Toast.makeText(UpdateBalActivity.this, R.string.error_sending_request, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });

                Log.d(TAG, "adding bal locally");
                // save new location in shared preferences
                Bal bal = new Bal(UUID.randomUUID().toString(), streetNumber.getText().toString(), streetName.getText().toString(), "", town.getText().toString(), postalCode.getText().toString(), "", newLocation.latitude, newLocation.longitude, Bal.Type.local, 0);
                Bal.saveNewBal(this, bal);

                // Add the request to the RequestQueue.
                queue.add(updateRequest);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("street_number", streetNumber.getText().toString());
        outState.putString("address", streetName.getText().toString());
        outState.putString("postal_code", postalCode.getText().toString());
        outState.putString("town", town.getText().toString());
        outState.putString("comment", comment.getText().toString());
        outState.putParcelable("newLocation", newLocation);
        outState.putBoolean("addressFoundWithGeocoder", addressFoundWithGeocoder);
    }
}
