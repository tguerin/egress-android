package fr.devoxx.egress.model;

import android.text.TextUtils;

import com.firebase.client.DataSnapshot;

import java.util.Map;

public class Station {

    public static final String FIELD_NAME = "name";
    public static final String FIELD_LATITUDE = "latitude";
    public static final String FIELD_LONGITUDE = "longitude";
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_OWNER_MAIL = "ownerMail";
    public static final String FIELD_WHEN = "when";

    private final String key;
    private final Map<String, Object> dataValues;

    public Station(DataSnapshot dataSnapshot) {
        this.dataValues = (Map<String, Object>) dataSnapshot.getValue();
        this.key = dataSnapshot.getKey();
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return (String) dataValues.get(FIELD_NAME);
    }

    public String getOwner() {
        return (String) dataValues.get(FIELD_OWNER);
    }

    public String getOwnerMail() {
        return (String) dataValues.get(FIELD_OWNER_MAIL);
    }

    public Double getLatitude() {
        return (Double) dataValues.get(FIELD_LATITUDE);
    }

    public Double getLongitude() {
        return (Double) dataValues.get(FIELD_LONGITUDE);
    }

    public boolean isFree() {
        return TextUtils.isEmpty(getOwner());
    }

    public boolean hasCoordinate() {
        return getLatitude() != null && getLongitude() != null;
    }
}
