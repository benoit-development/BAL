package org.bbt.bal.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bbt.bal.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bal bean
 */
public class Bal implements Parcelable {

    /**
     * enum for bal type
     */
    public static enum Type {
        local,
        server
    };

    /**
     * Tag for logs
     */
    private static final String TAG = "Bal";

    /**
     * Preferences name for server Bal
     */
    private static final String BAL_SHARED_PREFERENCES = "BAL_SHARED_PREFERENCES";

    /**
     * id of the bal
     */
    public String id;

    /**
     * bal streetNumber label
     */
    public String streetNumber;

    /**
     * street number extension
     */
    public String extension;

    /**
     * bal streetName label
     */
    public String streetName;

    /**
     * Town
     */
    public String town;

    /**
     * Postal code
     */
    public String postalCode;

    /**
     * latitude
     */
    public double latitude;

    /**
     * longitude
     */
    public double longitude;

    /**
     * Country code
     */
    public String countryCode;

    /**
     * type of the Bal
     */
    public Type type;

    /**
     * odrer
     */
    public int order;

    /**
     * Constructor
     *
     * @param id
     * @param streetNumber
     * @param streetName
     * @param extension
     * @param town
     * @param postalCode
     * @param countryCode
     * @param latitude
     * @param longitude
     * @param type
     * @param order
     */
    public Bal(String id, String streetNumber, String streetName, String extension, String town, String postalCode, String countryCode, double latitude, double longitude, Type type, int order) {
        this.id = id;
        this.streetNumber = streetNumber;
        this.streetName = streetName;
        this.extension = extension;
        this.town = town;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.order = order;
    }

    /**
     * Build a bal from a serialization
     *
     * @param serialization
     */
    public Bal(String serialization) {
        String[] split = serialization.split("%");
        if (split.length == 11) {
            id = split[0];
            streetNumber = split[1];
            streetName = split[2];
            extension = split[3];
            town = split[4];
            postalCode = split[5];
            countryCode = split[6];
            latitude = Double.parseDouble(split[7]);
            longitude = Double.parseDouble(split[8]);
            type = Type.valueOf(split[9]);
            order = Integer.valueOf(split[10]);
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(id);
        out.writeString(streetNumber);
        out.writeString(extension);
        out.writeString(streetName);
        out.writeString(town);
        out.writeString(postalCode);
        out.writeString(countryCode);
        out.writeDouble(latitude);
        out.writeDouble(longitude);
        out.writeString(type.name());
        out.writeInt(order);
    }

    public static final Parcelable.Creator<Bal> CREATOR
            = new Parcelable.Creator<Bal>() {
        public Bal createFromParcel(Parcel in) {
            return new Bal(in);
        }

        public Bal[] newArray(int size) {
            return new Bal[size];
        }
    };

    private Bal(Parcel in) {
        id = in.readString();
        streetNumber = in.readString();
        extension = in.readString();
        streetName = in.readString();
        town = in.readString();
        postalCode = in.readString();
        countryCode = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        type = Type.valueOf(in.readString());
        order = in.readInt();

    }

    @Override
    public String toString() {
        id = id.replaceAll("%", "");
        streetNumber = streetNumber.replaceAll("%", "");
        extension = extension.replaceAll("%", "");
        streetName = streetName.replaceAll("%", "");
        town = town.replaceAll("%", "");
        postalCode = postalCode.replaceAll("%", "");
        countryCode = countryCode.replaceAll("%", "");
        return id + "%" + streetNumber + "%" + extension + "%" + streetName + "%" + town + "%" + postalCode + "%" + countryCode + "%" + latitude + "%" + longitude + "%" + type.name() + "%" + order;
    }

    /**
     * make a mailbox update request
     *
     * @param context
     * @param id
     * @param latitude
     * @param longitude
     * @param newLatitude
     * @param newLongitude
     */
    public static void requestUpdate(final Context context, String id, double latitude, double longitude, double newLatitude, double newLongitude, String comment) {
        Log.d(TAG, "Send an update request : " + id + ", " + latitude + ", " + longitude + ", " + newLatitude + ", " + newLongitude);
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
        String url = null;
        try {
            url = context.getString(R.string.server_url)
                    + "bal_request_update.php?id=" + URLEncoder.encode(id, "UTF-8")
                    + "&old_latitude=" + latitude
                    + "&old_longitude=" + longitude
                    + "&new_latitude=" + newLatitude
                    + "&new_longitude=" + newLongitude
                    + "&comment=" + URLEncoder.encode(comment, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, "Error while url encoding");
            e.printStackTrace();
        }

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Toast.makeText(context, R.string.request_sent, Toast.LENGTH_LONG).show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.error_sending_request, Toast.LENGTH_LONG).show();
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    /**
     * make a mailbox update request
     *
     * @param context
     * @param bal
     */
    public static void requestAdd(final Context context, Bal bal, String comment) {
        Log.d(TAG, "Send an add request : " + bal.toString());
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
        String url = null;
        try {
            url = context.getString(R.string.server_url)
                    + "bal_request_add.php?"
                    + "street_number=" + URLEncoder.encode(bal.streetNumber, "UTF-8")
                    + "&street_name=" + URLEncoder.encode(bal.streetName, "UTF-8")
                    + "&town=" + URLEncoder.encode(bal.town, "UTF-8")
                    + "&postal_code=" + URLEncoder.encode(bal.postalCode, "UTF-8")
                    + "&latitude=" + bal.latitude
                    + "&longitude=" + bal.longitude
                    + "&comment=" + URLEncoder.encode(comment, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, "Error while url encoding");
            e.printStackTrace();
        }

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Toast.makeText(context, R.string.request_sent, Toast.LENGTH_LONG).show();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context, R.string.error_sending_request, Toast.LENGTH_LONG).show();
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    /**
     * Parse json response from server
     *
     * @param response
     * @return
     */
    public static Map<String, Bal> parseJsonBalList(String response) {
        Map<String, Bal> result = new HashMap<String, Bal>();

        java.lang.reflect.Type collectionType = new TypeToken<Collection<Bal>>() {
        }.getType();
        try {
            Collection<Bal> balCollection = new Gson().fromJson(response, collectionType);

            for (Bal bal : balCollection) {
                bal.type = Type.server;
                result.put(bal.id, bal);
            }
        } catch (Exception e) {
            Log.d(TAG, "Error parsing json");
        }

        return result;
    }

    /**
     * get first part of a formatted address
     *
     * @return
     */
    public String getAddress1() {
        String result = "";

        if ((streetNumber != null) && (streetNumber.length() > 0) && (!"0".equals(streetNumber))) {
            result += streetNumber + " ";
        }
        if ((extension != null) && (extension.length() > 0)) {
            result += extension + " ";
        }
        result += streetName;

        return result;
    }

    /**
     * get second part of a formatted address
     *
     * @return
     */
    public String getAddress2() {
        return postalCode + " " + town;
    }

    /**
     * gett a formatted full address
     *
     * @return
     */
    public String getCompleteAddress() {
        return getAddress1() + " " + getAddress2();
    }

    /**
     * Save new location in shared preferences for an existing bal
     *
     * @param context
     * @param bal
     */
    public static void saveNewBal(Context context, Bal bal) {
        Log.i(TAG, "save new location for existing bal : " + bal);
        SharedPreferences settings = context.getSharedPreferences(BAL_SHARED_PREFERENCES, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(bal.id, bal.toString()).commit();
    }

    /**
     * get list list of created Bal by user
     *
     * @param context
     * @return
     */
    public static List<Bal> getUserBalList(Context context) {
        SharedPreferences settings = context.getSharedPreferences(BAL_SHARED_PREFERENCES, 0);
        Map<String, ?> allBal = settings.getAll();

        ArrayList<Bal> result = new ArrayList<Bal>();

        for (Object value : allBal.values()) {
            if (value instanceof String) {
                Bal bal = new Bal((String) value);
                if ((bal.id != null) && (Type.local.equals(bal.type))) {
                    result.add(bal);
                }
            }
        }

        return result;
    }

    /**
     * get an updated Bal with local location if it has been updated by user
     *
     * @param context
     * @param bal
     * @return
     */
    public static Bal getBalWithLocalLocation(Context context, Bal bal) {
        SharedPreferences settings = context.getSharedPreferences(BAL_SHARED_PREFERENCES, 0);
        String localBal = settings.getString(bal.id, null);
        if (localBal != null) {
            Log.d(TAG, "Local location saved : " + bal.toString());
            return new Bal(localBal);
        }
        return bal;
    }

    /**
     * Delete a local bal
     * @param context
     * @param bal
     */
    public static void deleteBalFromSharedPreferences(Context context, Bal bal) {
        Log.i(TAG, "Deleteing bal : " + bal.toString());
        SharedPreferences settings = context.getSharedPreferences(BAL_SHARED_PREFERENCES, 0);
        if (settings.contains(bal.id)) {
            settings.edit().remove(bal.id).apply();
        }
    }

    /**
     * Test if it's a local bal
     * @return
     */
    public boolean isLocal() {
        return Type.local.equals(type);
    }

    /**
     * Test if it's a server bal
     * @return
     */
    public boolean isServer() {
        return Type.server.equals(type);
    }
}