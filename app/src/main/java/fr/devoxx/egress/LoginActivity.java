package fr.devoxx.egress;

import android.accounts.Account;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import fr.devoxx.egress.model.Player;

import static android.widget.Toast.LENGTH_LONG;


public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 1001;
    private static final int REQUEST_CHECK_SERVICES = 1002;

    private GoogleApiClient googleApiClient;

    private Handler handler = new Handler();

    @InjectView(R.id.sign_in_button)
    SignInButton signInButton;
    @InjectView(R.id.sign_in_progress)
    ProgressBar signInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);
        ButterKnife.inject(this);
        signInButton.setVisibility(View.VISIBLE);
        signInProgress.setVisibility(View.GONE);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(BuildConfig.OAUTH_TOKEN)
                .build();

        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        if (connectionResult.hasResolution()) {
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                connectionResult.startResolutionForResult(LoginActivity.this, REQUEST_CHECK_SERVICES);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                                Toast.makeText(LoginActivity.this, R.string.no_play_services, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, R.string.no_play_services, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    @OnClick(R.id.sign_in_button)
    public void onSignInClicked() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (!result.isSuccess() || result.getSignInAccount() == null) {
                signInButton.setVisibility(View.VISIBLE);
                signInProgress.setVisibility(View.GONE);
                Toast.makeText(this, R.string.login_failed, LENGTH_LONG).show();
            } else {
                goToMapsScreen(result);
            }
        }
    }

    private void goToMapsScreen(final GoogleSignInResult result) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final GoogleSignInAccount signInAccount = result.getSignInAccount();
                final String token = getToken(signInAccount.getEmail());
                if (token == null) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                           // TODO toast error
                        }
                    });

                } else {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            signInButton.setVisibility(View.GONE);
                            signInProgress.setVisibility(View.VISIBLE);
                            Intent intent = new Intent(LoginActivity.this, MapsActivity.class);
                            intent.putExtra(MapsActivity.EXTRA_PLAYER, new Player(token, signInAccount.getDisplayName(), signInAccount.getEmail(), 0));
                            startActivity(intent);
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    @Nullable
    private String getToken(String mail) {
        try {
            Account account = new Account(mail, "com.google");
            return GoogleAuthUtil.getToken(this, account, "oauth2:profile");
        } catch (IOException | GoogleAuthException e) {
            return null;
        }
    }

}
