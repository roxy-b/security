package com.example.gpscoordinates.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class APIClient {

    private static APIClient instance = null;
    private APIInterface myApi;

    private APIClient() {
        Retrofit retrofit = new Retrofit.Builder().baseUrl(APIInterface.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        myApi = retrofit.create(APIInterface.class);
    }

    public static synchronized APIClient getInstance() {
        if (instance == null) {
            instance = new APIClient();
        }
        return instance;
    }

    public APIInterface getMyApi() {
        return myApi;
    }
}
