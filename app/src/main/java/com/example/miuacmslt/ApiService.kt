package com.example.miuacmslt

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("/v2/directions/driving-car")

    //APIKEY,COORDENADAS DE INICIO Y COORDENADAS FINAL
    suspend fun getRoute(
        @Query("api_key") key: String,
        @Query("start", encoded = true) start: String,
        @Query("end", encoded = true) end: String
    ):Response<RouteResponse>//Objeto para parsear los datos de Json
}