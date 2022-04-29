package com.example.gpscoordinates.network;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface APIInterface {
    String BASE_URL = "http://146.148.70.212:8085/";

    @GET("sensor")
    Call<Reply> postLocationUpdate(
            @Query("devId") String devId,
            @Query("devKey") String devKey,
            @Query("sval") String sval
    );
}
