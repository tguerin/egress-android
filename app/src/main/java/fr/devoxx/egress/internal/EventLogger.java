package fr.devoxx.egress.internal;

import android.net.Uri;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import timber.log.Timber;

import static fr.devoxx.egress.BuildConfig.LOG_NEW_PLAYER_URL;
import static fr.devoxx.egress.BuildConfig.LOG_NEW_STATION_CAPTURED_URL;
import static fr.devoxx.egress.BuildConfig.LOG_STATION_DISCOVERED_URL;

public class EventLogger {

    private OkHttpClient httpClient = new OkHttpClient();

    public void logNewPlayer(String playerName) {
        Timber.d("Sending request %s", LOG_NEW_PLAYER_URL);
        Request request = new Request.Builder().
                url(String.format(LOG_NEW_PLAYER_URL, Uri.encode(playerName)))
                .build();
        httpClient.newCall(request).enqueue(new EmptyResponseCallback());
    }

    public void logStationDiscovered(String stationKey) {
        Timber.d("Sending request %s", LOG_STATION_DISCOVERED_URL);
        Request request = new Request.Builder().
                url(String.format(LOG_STATION_DISCOVERED_URL, stationKey))
                .build();
        httpClient.newCall(request).enqueue(new EmptyResponseCallback());
    }

    public void logStationCapturedBy(String stationKey, String playerName) {
        Timber.d("Sending request %s", LOG_NEW_STATION_CAPTURED_URL);
        Request request = new Request.Builder().
                url(String.format(LOG_NEW_STATION_CAPTURED_URL, stationKey, Uri.encode(playerName)))
                .build();
        httpClient.newCall(request).enqueue(new EmptyResponseCallback());
    }

    private static class EmptyResponseCallback implements Callback {
        @Override
        public void onFailure(Request request, IOException e) {
            Timber.d(e, "Failed to send request : %s", request.url());
        }

        @Override
        public void onResponse(Response response) throws IOException {
            // Ignore
        }
    }
}
