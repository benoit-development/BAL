package org.bbt.bal.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.bbt.bal.R;
import org.bbt.bal.tools.Bal;

import java.net.URLEncoder;

public class UpdateBalActivity extends AbstractBalFormActivity {

    /**
     * Intent extra to send a serialized bal to update
     */
    public static final String INTENT_EXTRA_BAL = "org.bbt.bal.activity.UpdateActivity.bal";

    /**
     * Tag for logs
     */
    private static final String TAG = "UpdateBalActivity";

    /**
     * Bal to update
     */
    protected Bal bal;

    /**
     * From bal
     */
    protected Marker markerFrom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        bal = (Bal) getIntent().getParcelableExtra(INTENT_EXTRA_BAL);
        newLocation = (LatLng) getIntent().getParcelableExtra(INTENT_EXTRA_NEW_LOCATION);
        cameraLocation = (LatLng) getIntent().getParcelableExtra(INTENT_EXTRA_CAMERA_LAT_LNG);
        mapType = getIntent().getIntExtra(INTENT_EXTRA_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
        cameraZoom = getIntent().getIntExtra(INTENT_EXTRA_CAMERA_ZOOM, 15);

        if (Bal.Type.server.equals(bal.type)) {
            setContentView(R.layout.activity_form_server_bal);
        } else {
            setContentView(R.layout.activity_form_local_bal);
            streetNumber = (EditText) findViewById(R.id.street_number);
            streetName = (EditText) findViewById(R.id.street_name);
            postalCode = (EditText) findViewById(R.id.postal_code);
            town = (EditText) findViewById(R.id.town);
        }

        // check if all information are available
        if ((bal == null) || (newLocation == null)) {
            Toast.makeText(this, R.string.error_displaying_update_form, Toast.LENGTH_LONG).show();
            finish();
        }

        // set view
        setTitle(bal.getCompleteAddress());
        comment = (EditText) findViewById(R.id.comment);
        mapView = findViewById(R.id.map);

        // update activity state
        if (savedInstanceState != null) {
            comment.setText(savedInstanceState.getString("comment", ""));
            newLocation = (LatLng) savedInstanceState.getParcelable("new_location");

            if (Bal.Type.local.equals(bal.type)) {
                streetNumber.setText(savedInstanceState.getString("street_number", bal.streetNumber));
                streetName.setText(savedInstanceState.getString("street_name", bal.streetName));
                postalCode.setText(savedInstanceState.getString("postal_code", bal.postalCode));
                town.setText(savedInstanceState.getString("town", bal.town));
            }
        } else {
            if (bal.isLocal()) {
                streetNumber.setText(bal.streetNumber);
                streetName.setText(bal.streetName);
                postalCode.setText(bal.postalCode);
                town.setText(bal.town);
            }
        }

        if (bal.isLocal()) {
            requestAddressFromGeocoder();
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

        // add markers
        float iconColor = BitmapDescriptorFactory.HUE_YELLOW;
        if (Bal.Type.local.equals(bal.type)) {
            iconColor = BitmapDescriptorFactory.HUE_ORANGE;
        }
        markerFrom = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(bal.latitude, bal.longitude))
                .title(getString(R.string.current_location))
                .icon(BitmapDescriptorFactory.defaultMarker(iconColor)));
        markerNew = mMap.addMarker(new MarkerOptions()
                .position(newLocation)
                .draggable(true)
                .title(getString(R.string.new_location))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        markerNew.showInfoWindow();

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                updateCameraPosition();
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
                if (bal.isLocal()) {
                    requestAddressFromGeocoder();
                }
            }
        });
    }

    /**
     * update zoom and location
     */
    private void updateCameraPosition() {
        // prepare bounds
        LatLng sw = new LatLng(((bal.latitude < newLocation.latitude)?bal.latitude:newLocation.latitude), ((bal.longitude < newLocation.longitude)?bal.longitude:newLocation.longitude));
        LatLng ne = new LatLng(((bal.latitude > newLocation.latitude)?bal.latitude:newLocation.latitude), ((bal.longitude > newLocation.longitude)?bal.longitude:newLocation.longitude));
        LatLngBounds bounds = new LatLngBounds(sw, ne);

        // prepare correct padding
        int height = mapView.getHeight();
        int width =  mapView.getWidth();
        int standardPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
        if ((standardPadding * 2) > Math.min(height, width)) {
            standardPadding = Math.min(height, width) / 3;
        }

        // steup camera position and padding
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, standardPadding);
        mMap.animateCamera(cu);
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

            Log.d(TAG, "saving change");
            Log.d(TAG, "from : " + bal.latitude + ", " + bal.longitude);
            Log.d(TAG, " to : " + newLocation.latitude + ", " + newLocation.longitude);
            Log.d(TAG, "comment : " + comment.getText().toString());

            Log.d(TAG, "server change requested");
            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(this);
            try {
                String url = getString(R.string.server_url);
                if (Bal.Type.server.equals(bal.type)) {
                    url +=  "bal_request_update.php?id=" + URLEncoder.encode(bal.id, "UTF-8")
                            + "&old_latitude=" + bal.latitude
                            + "&old_longitude=" + bal.longitude
                            + "&new_latitude=" + newLocation.latitude
                            + "&new_longitude=" + newLocation.longitude
                            + "&comment=" + URLEncoder.encode(comment.getText().toString(), "UTF-8");
                } else {
                    url += "bal_request_add.php?"
                            + "&street_number=" + URLEncoder.encode(streetNumber.getText().toString(), "UTF-8")
                            + "&street_name=" + URLEncoder.encode(streetName.getText().toString(), "UTF-8")
                            + "&postal_code=" + URLEncoder.encode(postalCode.getText().toString(), "UTF-8")
                            + "&town=" + URLEncoder.encode(town.getText().toString(), "UTF-8")
                            + "&latitude=" + newLocation.latitude
                            + "&longitude=" + newLocation.longitude
                            + "&comment=" + URLEncoder.encode(comment.getText().toString(), "UTF-8");
                }

                // Request a string response from the provided URL.
                StringRequest updateRequest = new StringRequest(Request.Method.GET, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.d(TAG, "request sent");
                                Toast.makeText(UpdateBalActivity.this, R.string.request_sent, Toast.LENGTH_LONG).show();
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

                Log.d(TAG, "local change requested");
                // save new location in shared preferences
                bal.latitude = newLocation.latitude;
                bal.longitude = newLocation.longitude;
                Bal.saveNewBal(this, bal);

                // Add the request to the RequestQueue.
                queue.add(updateRequest);
            } catch (Exception e) {
                Log.e(TAG, "error sending request : " + e.getMessage());
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("new_location", newLocation);
        outState.putString("comment", comment.getText().toString());

        if (Bal.Type.local.equals(bal.type)) {
            outState.putString("street_number", streetNumber.getText().toString());
            outState.putString("street_name", streetName.getText().toString());
            outState.putString("postal_code", postalCode.getText().toString());
            outState.putString("town", town.getText().toString());
        }
    }
}
