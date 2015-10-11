package fr.devoxx.egress.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Player implements Parcelable {

    public static final String FIELD_SCORE = "score";
    public static final String FIELD_NAME = "name";

    public final String token;
    public final String name;
    public final String mail;
    public long score;

    public Player(String token, String name, String mail, long score) {
        this.token = token;
        this.name = name;
        this.mail = mail;
        this.score = score;
    }

    protected Player(Parcel in) {
        token = in.readString();
        name = in.readString();
        mail = in.readString();
        score = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(token);
        dest.writeString(name);
        dest.writeString(mail);
        dest.writeLong(score);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Player> CREATOR = new Parcelable.Creator<Player>() {
        @Override
        public Player createFromParcel(Parcel in) {
            return new Player(in);
        }

        @Override
        public Player[] newArray(int size) {
            return new Player[size];
        }
    };
}
