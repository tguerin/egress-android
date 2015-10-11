package fr.devoxx.egress;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Property;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.MutableData;
import com.firebase.client.Transaction;
import com.firebase.client.ValueEventListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.melnykov.fab.FloatingActionButton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import fr.devoxx.egress.internal.EventLogger;
import fr.devoxx.egress.internal.LatLngInterpolator;
import fr.devoxx.egress.internal.Preferences;
import fr.devoxx.egress.model.Player;
import fr.devoxx.egress.model.Station;
import icepick.Icepick;
import icepick.Icicle;
import timber.log.Timber;

import static android.widget.Toast.LENGTH_LONG;
import static fr.devoxx.egress.internal.Functions.distance;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final String EXTRA_PLAYER = "fr.devoxx.egress.EXTRA_PLAYER";

    private static final LatLng NANCY_GEO_POSITION = new LatLng(48.669075, 6.155275);
    private static final LatLngInterpolator latLngInterpolator = new LatLngInterpolator.LinearFixed();
    private static final int CIRCLE_HINT_RADIUS = 1000;
    private static final int RC_REQUEST_CHECK_SETTINGS = 1;
    private static final int RC_REQUEST_CHECK_SERVICES = 2;
    private static final int RC_REQUEST_LOCATION_PERMISSIONS = 3;

    @InjectView(R.id.add_action)
    FloatingActionButton addActionButton;
    @InjectView(R.id.welcome)
    TextView welcomeView;
    @InjectView(R.id.status)
    TextView statusView;
    @InjectView(R.id.total_captures)
    TextView totalCapturesView;
    @InjectView(R.id.leader_board_card)
    ViewGroup leaderBoardCardView;
    @InjectView(R.id.leader_board_container)
    ViewGroup leaderBoardContainerView;

    private GoogleMap map; // Might be null if Google Play services APK is not available.

    private GeoFire geoFire;
    private Firebase firebase;
    private GeoQuery geoQuery;

    private Circle circle;
    private boolean circleHasMoved = false;
    @Icicle
    LatLng lastCircleCenter;
    private int circleFillColor;
    private ObjectAnimator circleHintAnimator;

    private Map<String, Marker> displayedMarkersCache = new HashMap<>();
    private Map<Marker, Station> displayedStationsCache = new HashMap<>();

    private BitmapDescriptor freeMarkerIconDescriptor;
    private BitmapDescriptor pendingMarkerIconDescriptor;
    private BitmapDescriptor lockedMarkerIconDescriptor;
    private BitmapDescriptor ownedMarkerIconDescriptor;

    private float hideActionButtonOffset;

    private Marker selectedMarker;

    private StationValueEventListener stationValueEventListener = new StationValueEventListener();

    private Player player;
    private SupportMapFragment mapFragment;

    private Set<String> pendingCaptures = new HashSet<>();

    private EventLogger eventLogger = new EventLogger();

    private Boolean locationServicesEnabled;

    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.maps_activity);
        init(savedInstanceState);
        setUpActionButton();
        setUpGeoFire();
        setUpPlayerInfos();
        setUpLeaderBoard();
        if (savedInstanceState == null) {
            eventLogger.logNewPlayer(player.name);
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        if (connectionResult.hasResolution()) {
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                connectionResult.startResolutionForResult(MapsActivity.this, RC_REQUEST_CHECK_SERVICES);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                                locationServicesEnabled = false;
                            }
                        } else {
                            locationServicesEnabled = false;
                        }
                    }
                })
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        circleHasMoved = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length == 0) {
            return;
        }
        if (requestCode == RC_REQUEST_LOCATION_PERMISSIONS) {
            locationServicesEnabled = permissions.length == 1 &&
                    permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
            setupMyLocation();
        }
    }

    private void setupMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            setUpMapInfos(NANCY_GEO_POSITION);
        } else {
            map.setMyLocationEnabled(true);
            final Toast toast = Toast.makeText(this, "Localisation en cours...", LENGTH_LONG);
            toast.show();
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, LocationRequest.create().
                    setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY), new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    toast.cancel();
                    setUpMapInfos(new LatLng(location.getLatitude(), location.getLongitude()));
                    LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
                }
            });
        }
    }

    private void init(Bundle savedInstanceState) {
        ButterKnife.inject(this);
        Icepick.restoreInstanceState(this, savedInstanceState);
        mapFragment = SupportMapFragment.newInstance(new GoogleMapOptions().mapType(GoogleMap.MAP_TYPE_TERRAIN));
        getSupportFragmentManager().beginTransaction().
                replace(R.id.map, mapFragment).
                commit();
        mapFragment.getMapAsync(this);
        player = getIntent().getParcelableExtra(EXTRA_PLAYER);
        welcomeView.setText(getString(R.string.welcome, player.name));
        totalCapturesView.setText(getString(R.string.total_captures, "--"));
    }


    private void setUpActionButton() {
        hideActionButtonOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
        addActionButton.setTranslationY(hideActionButtonOffset);
    }

    private void setUpGeoFire() {
        firebase = new Firebase(BuildConfig.FIREBASE_URL);
        firebase.authWithOAuthToken("google", player.token, new AuthenticationEventListener());
        firebase.child(".info").child("connected").addValueEventListener(new ConnectionStateListener());
        geoFire = new GeoFire(firebase.child("_geofire"));
    }

    private void setUpPlayerInfos() {
        final MapsActivity context = MapsActivity.this;
        if (!Preferences.hasPlayerId(context)) {
            firebase.child("players").orderByChild("mail").
                    startAt(player.mail).
                    endAt(player.mail).
                    addListenerForSingleValueEvent(new GetPlayerInfosListener());
        } else {
            firebase.child("players").
                    child(Preferences.getPlayerId(context)).
                    addValueEventListener(new PlayerScoreListener());
        }
    }

    private void setUpLeaderBoard() {
        leaderBoardCardView.setVisibility(View.GONE);
        firebase.child("players").orderByChild("score").
                limitToLast(3).
                addValueEventListener(new LeaderBoardListener());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMapToolbarEnabled(false);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RC_REQUEST_LOCATION_PERMISSIONS);
        } else {
            setupMyLocation();
        }
        freeMarkerIconDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.ic_maps_train_station);
        pendingMarkerIconDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.ic_maps_train_station_pending);
        lockedMarkerIconDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.ic_maps_train_station_locked);
        ownedMarkerIconDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.ic_maps_train_station_owned);
    }

    private void setUpMapInfos(LatLng location) {
        if (map == null) {
            return;
        }

        lastCircleCenter = location;

        setupCircleHint();

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastCircleCenter, 13));

        geoQuery = geoFire.queryAtLocation(new GeoLocation(lastCircleCenter.latitude, lastCircleCenter.longitude), 1);
        geoQuery.addGeoQueryEventListener(new StationGeoQueryListener());

        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (distance(latLng, lastCircleCenter) > CIRCLE_HINT_RADIUS) {
                    selectedMarker = null;
                    lastCircleCenter = latLng;
                    circleHasMoved = true;
                    animateCircle(circle, latLng, latLngInterpolator);
                    geoQuery.setCenter(new GeoLocation(latLng.latitude, latLng.longitude));
                    addActionButton.animate().translationY(hideActionButtonOffset).start();
                }
            }
        });

        map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                selectedMarker = marker;
                TrainStationInfoWindowView infoWindowView = (TrainStationInfoWindowView) LayoutInflater.from(MapsActivity.this).
                        inflate(R.layout.train_station_info_window_view, null);
                Station station = displayedStationsCache.get(marker);
                infoWindowView.bind(station);
                addActionButton.animate().translationY(station.isFree() && !pendingCaptures.contains(station.getKey()) ? 0 : hideActionButtonOffset).start();
                return infoWindowView;
            }
        });

        map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                selectedMarker = null;
                marker.hideInfoWindow();
                addActionButton.animate().translationY(hideActionButtonOffset).start();
            }
        });
    }

    private void setupCircleHint() {
        if (circle == null) {
            circleFillColor = getResources().getColor(R.color.circle_color);
            CircleOptions circleOptions = new CircleOptions()
                    .center(lastCircleCenter)
                    .fillColor(circleFillColor)
                    .strokeColor(getResources().getColor(R.color.circle_color))
                    .radius(CIRCLE_HINT_RADIUS);
            circle = map.addCircle(circleOptions);
        }
    }

    void animateCircle(final Circle circle, LatLng finalPosition, final LatLngInterpolator latLngInterpolator) {
        if (circleHintAnimator != null) {
            circleHintAnimator.cancel();
        }
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return latLngInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Circle, LatLng> property = Property.of(Circle.class, LatLng.class, "center");
        circleHintAnimator = ObjectAnimator.ofObject(circle, property, typeEvaluator, finalPosition);
        circleHintAnimator.addListener(new Animator.AnimatorListener() {
            private boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                for (Marker marker : displayedMarkersCache.values()) {
                    marker.setVisible(false);
                }
                circle.setFillColor(Color.TRANSPARENT);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                circleHasMoved = false;
                if (!cancelled) {
                    circle.setFillColor(circleFillColor);
                    for (Marker marker : displayedMarkersCache.values()) {
                        marker.setVisible(true);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        circleHintAnimator.setDuration(1000);
        circleHintAnimator.start();
    }

    @OnClick(R.id.add_action)
    public void onAddActionClicked() {
        final Station station = displayedStationsCache.get(selectedMarker);
        pendingCaptures.add(station.getKey());
        addActionButton.animate().translationY(hideActionButtonOffset).start();
        firebase.child("stations").child(station.getKey()).runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                mutableData.child(Station.FIELD_OWNER).setValue(player.name);
                mutableData.child(Station.FIELD_OWNER_MAIL).setValue(player.mail);
                mutableData.child(Station.FIELD_WHEN).setValue(System.currentTimeMillis());
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean committed, DataSnapshot dataSnapshot) {
                pendingCaptures.remove(station.getKey());
                if (firebaseError != null) {
                    handleStationUpdated(dataSnapshot);
                    addActionButton.animate().translationY(hideActionButtonOffset).start();
                    Toast.makeText(MapsActivity.this, R.string.too_late, LENGTH_LONG).show();
                } else if (committed) {
                    Station station = new Station(dataSnapshot);
                    if (player.mail.equals(station.getOwnerMail())) {
                        firebase.child("players").
                                child(Preferences.getPlayerId(MapsActivity.this)).
                                child(Player.FIELD_SCORE).setValue(++player.score);
                        eventLogger.logStationCapturedBy(station.getKey(), player.name);
                    }
                    handleStationUpdated(dataSnapshot);
                }
            }
        });
    }

    private void handleStationUpdated(DataSnapshot dataSnapshot) {
        Station station = new Station(dataSnapshot);
        if (!station.hasCoordinate()) {
            return;
        }
        Marker marker = displayedMarkersCache.get(station.getKey());
        if (marker == null) {
            marker = map.addMarker(new MarkerOptions()
                    .position(new LatLng(station.getLatitude(), station.getLongitude()))
                    .visible(!circleHasMoved)
                    .title(station.getName()));
        }
        marker.setIcon(getMarkerIcon(station));

        displayedMarkersCache.put(dataSnapshot.getKey(), marker);
        displayedStationsCache.put(marker, station);
    }


    private class AuthenticationEventListener implements Firebase.AuthResultHandler {

        @Override
        public void onAuthenticated(AuthData authData) {
            Timber.d("Authenticated");
        }

        @Override
        public void onAuthenticationError(FirebaseError firebaseError) {
            Timber.d("Authentication error");
        }
    }

    private class StationValueEventListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Station station = new Station(dataSnapshot);
            if (!station.hasCoordinate()) {
                return;
            }
            Marker marker = displayedMarkersCache.get(station.getKey());
            if (marker == null) {
                marker = map.addMarker(new MarkerOptions()
                        .position(new LatLng(station.getLatitude(), station.getLongitude()))
                        .visible(!circleHasMoved)
                        .title(station.getName()));
            }
            marker.setIcon(getMarkerIcon(station));

            displayedMarkersCache.put(dataSnapshot.getKey(), marker);
            displayedStationsCache.put(marker, station);
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
            Timber.d("Couldn't get station data %s", firebaseError);
        }
    }

    private BitmapDescriptor getMarkerIcon(Station station) {
        if (pendingCaptures.contains(station.getKey())) {
            return pendingMarkerIconDescriptor;
        } else if (station.isFree()) {
            return freeMarkerIconDescriptor;
        } else {
            return player.mail.equalsIgnoreCase(station.getOwnerMail()) ? ownedMarkerIconDescriptor : lockedMarkerIconDescriptor;
        }
    }

    private class GetPlayerInfosListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if (dataSnapshot.getValue() == null) {
                createPlayer();
            } else {
                DataSnapshot firstChild = dataSnapshot.getChildren().iterator().next();
                Map<String, Object> mapPlayerInfos = (Map<String, Object>) firstChild.getValue();
                Preferences.setPlayerId(MapsActivity.this, firstChild.getKey());
                player.score = (long) mapPlayerInfos.get(Player.FIELD_SCORE);
                totalCapturesView.setText(getString(R.string.total_captures, mapPlayerInfos.get(Player.FIELD_SCORE)));
            }
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
            Timber.d(firebaseError.getMessage());
        }
    }

    private void createPlayer() {
        Firebase pushRef = firebase.child("players").push();
        pushRef.setValue(player, new Firebase.CompletionListener() {
            @Override
            public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                if (firebaseError == null) {
                    Preferences.setPlayerId(MapsActivity.this, firebase.getKey());
                    firebase.addValueEventListener(new PlayerScoreListener());
                } else {
                    Toast.makeText(MapsActivity.this, R.string.player_creation_error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private class LeaderBoardListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            leaderBoardCardView.setVisibility(View.VISIBLE);
            Iterator<DataSnapshot> playerIt = dataSnapshot.getChildren().iterator();
            leaderBoardContainerView.removeAllViews();
            int index = 0;
            while (playerIt.hasNext()) {
                Map<String, Object> mapPlayerInfos = (Map<String, Object>) playerIt.next().getValue();
                TextView playerCellView = new TextView(MapsActivity.this);
                playerCellView.setText(getString(R.string.leader_board_entry,
                        3 - index,
                        mapPlayerInfos.get(Player.FIELD_NAME),
                        mapPlayerInfos.get(Player.FIELD_SCORE)));
                leaderBoardContainerView.addView(playerCellView, leaderBoardContainerView.getChildCount() - index);
                index++;
            }
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
            Timber.d("Couldn't get leader board %s", firebaseError);
        }
    }

    private class PlayerScoreListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Map<String, Object> mapPlayerInfos = (Map<String, Object>) dataSnapshot.getValue();
            player.score = (long) mapPlayerInfos.get(Player.FIELD_SCORE);
            totalCapturesView.setText(getString(R.string.total_captures, mapPlayerInfos.get(Player.FIELD_SCORE)));
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
            Timber.d("Couldn't get player score %s", firebaseError);
        }
    }

    private class ConnectionStateListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if (Boolean.TRUE.equals(dataSnapshot.getValue())) {
                statusView.setText(R.string.connected);
                statusView.setTextColor(getResources().getColor(R.color.connected));
            } else {
                statusView.setText(R.string.disconnected);
                statusView.setTextColor(getResources().getColor(R.color.disconnected));
            }
        }

        @Override
        public void onCancelled(FirebaseError firebaseError) {
            Timber.d("Couldn't get connection state %s", firebaseError);
        }
    }

    private class StationGeoQueryListener implements GeoQueryEventListener {
        @Override
        public void onKeyEntered(final String key, final GeoLocation location) {
            Timber.d("On key entered " + location);
            firebase.child("stations").child(key).addValueEventListener(stationValueEventListener);
            eventLogger.logStationDiscovered(key);
        }

        @Override
        public void onKeyExited(String key) {
            Timber.d("On key exited " + key);
            firebase.child("stations").child(key).removeEventListener(stationValueEventListener);
            Marker markerToRemove = displayedMarkersCache.remove(key);
            if (markerToRemove != null) {
                displayedStationsCache.remove(markerToRemove);
                markerToRemove.remove();
            }
        }

        @Override
        public void onKeyMoved(String key, GeoLocation location) {
            Timber.d("On key moved " + key);
        }

        @Override
        public void onGeoQueryReady() {
            Timber.d("Geo query ready");
        }

        @Override
        public void onGeoQueryError(FirebaseError error) {
            Timber.d("Geo query error " + error);
        }
    }
}
