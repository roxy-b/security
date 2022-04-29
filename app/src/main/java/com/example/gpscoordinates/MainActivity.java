package com.example.gpscoordinates;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.work.WorkManager;

import android.Manifest;

import com.example.gpscoordinates.network.APIClient;
import com.example.gpscoordinates.network.Reply;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CHECK_SETTINGS = 101;
    private static final String TAG = "MainActivity";
    private String requestState = "";
    private TextView textView;
    private FusedLocationProviderClient fusedLocationClient;    //api client for last location and callback updates
    private LocationRequest locationRequest;                    //for location request
    private LocationCallback locationCallback;
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm:ss");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.txtState);

        checkPermission();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        getLastLocation();
        setupLocationUpdate();
        setupGPSCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isPermissionApproved())
        startLocationUpdates();
    }

    private boolean isPermissionApproved(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        else
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /*
        This method checks for 3 location permissions i.e. Fine, Coarse and Background. Then asks for
        missing permission.
    */
    private void checkPermission() {
        ActivityResultLauncher<String[]> locationPermissionRequest =
                registerForActivityResult(new ActivityResultContracts
                                .RequestMultiplePermissions(), result -> {
                            Boolean fineLocationGranted = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                fineLocationGranted = result.getOrDefault(
                                        Manifest.permission.ACCESS_FINE_LOCATION, false);
                            }
                            Boolean coarseLocationGranted = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                coarseLocationGranted = result.getOrDefault(
                                        Manifest.permission.ACCESS_COARSE_LOCATION, false);
                            }
                            Boolean backgroundLocationGranted = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundLocationGranted = result.getOrDefault(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION, false);
                            }
                    if (fineLocationGranted == null || !fineLocationGranted ||
                            coarseLocationGranted == null || !coarseLocationGranted ||
                            backgroundLocationGranted == null || !backgroundLocationGranted) {
                                textView.setText("Need location permission");
                            }
                    if (backgroundLocationGranted != null && !backgroundLocationGranted) {
                                // Background location access not granted.
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                showToast("Permission > Location > allow all the time");
                                Snackbar.make(getWindow().getDecorView().getRootView(),
                                        "Permission > Location > allow all the time", Snackbar.LENGTH_LONG)
                                        .show();
                                startActivity(intent);
                            }
                        }
                );

// ...
// Before you perform the actual permission request, check whether your app
// already has the permissions, and whether your app needs to show a permission
// rationale dialog. For more details, see Request permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.v(TAG, "Permission Request > Q");
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
            });
        } else {
            Log.v(TAG, "Permission Request < Q");
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

/*
    This method creates request for GPS location updates
*/
    protected void setupLocationUpdate() {
        //create request
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(30000);         //frequency
        //locationRequest.setMaxWaitTime(120000);     //latency
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //add request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        //get location
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            Log.v(TAG, "Location Setting Response success");
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(MainActivity.this,
                            REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException sendEx) {
                    // Ignore the error.
                    Log.v(TAG, "Location Setting Response Fail".concat(String.valueOf(sendEx)));

                }
            }
        });
    }

/*
    this method receives last location of device, on success setup to receive location updates
*/
    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        Log.v(TAG, "Last location Success");
                        // Logic to handle location object

                    }
                });
    }

    private void setupGPSCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Log.v(TAG, "Location Received");
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // ...
                    Log.v(TAG, "Location Received at: " + simpleDateFormat.
                            format(new Date(System.currentTimeMillis())));
                    Log.v(TAG, "Location Received Lat,Long :".concat(String.valueOf(location.getLatitude()))
                            .concat(String.valueOf(location.getLongitude())));
                    textView.setText(location.toString());

                    postGPSData(location);

                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void postGPSData(Location location) {
        Call<Reply> call = APIClient.getInstance().getMyApi().postLocationUpdate("KfxPumpCon1","PC1"
                ,location.toString()
        );
        call.enqueue(new Callback<Reply>() {
            @Override
            public void onResponse(Call<Reply> call, Response<Reply> response) {
                Log.d(TAG, "Api onResponse");

                if (response.body() != null) {
                    showToast(response.body().Reply);
                    Log.d(TAG, "Api onResponse".concat(response.body().Reply));

                }

            }

            @Override
            public void onFailure(Call<Reply> call, Throwable t) {
                showToast("An error has occured".concat(t.toString()));
                Log.e(TAG,"API onError: ".concat(t.toString()));
            }

        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}