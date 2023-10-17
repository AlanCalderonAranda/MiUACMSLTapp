package com.example.miuacmslt

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*


class calcularDistancia {

    fun calculaDistancia(point1: LatLng, point2: LatLng): Double {
        val radiusOfEarth = 6371.0 // Radio de la Tierra en kilómetros

        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distanceInKilometers = radiusOfEarth * c
        return distanceInKilometers * 1000 // Convertir a metros
    }
}