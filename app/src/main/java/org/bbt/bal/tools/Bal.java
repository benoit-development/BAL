package org.bbt.bal.tools;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Bal bean
 */
public class Bal implements Parcelable {

    /**
     * Tag for logs
     */
    private static final String TAG = "Bal";

    /**
     * id of the bal
     */
    public String id;

    /**
     * bal streetNumber label
     */
    private String streetNumber;

    /**
     * street number extension
     */
    private String extension;

    /**
     * bal streetName label
     */
    private String streetName;

    /**
     * Town
     */
    private String town;

    /**
     * Postal code
     */
    private String postalCode;

    /**
     * latitude
     */
    public final double latitude;

    /**
     * longitude
     */
    public final double longitude;

    /**
     * Country code
     */
    private String countryCode;

    /**
     * order
     */
    private final int order;

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
        return id + "%" + streetNumber + "%" + extension + "%" + streetName + "%" + town + "%" + postalCode + "%" + countryCode + "%" + latitude + "%" + longitude + "%" + order;
    }

    /**
     * Parse json response from server
     *
     * @param response json response received
     *
     * @return a {@link Bal} instance
     */
    public static Map<String, Bal> parseJsonBalList(String response) {
        Map<String, Bal> result = new HashMap<>();

        java.lang.reflect.Type collectionType = new TypeToken<Collection<Bal>>() {
        }.getType();
        try {
            Collection<Bal> balCollection = new Gson().fromJson(response, collectionType);

            for (Bal bal : balCollection) {
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
     * @return first part of bal address
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
     * @return second part of bal address
     */
    public String getAddress2() {
        return postalCode + " " + town;
    }

    /**
     * get a formatted full address
     *
     * @return complete address on line
     */
    public String getCompleteAddress() {
        return getAddress1() + " " + getAddress2();
    }
}