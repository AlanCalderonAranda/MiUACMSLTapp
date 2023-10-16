package com.example.miuacmslt

import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback, OnMyLocationButtonClickListener,
    TextToSpeech.OnInitListener {
    //Para el Mapa
    private lateinit var map: GoogleMap
    private lateinit var btnCalculate: Button

    //Para voz a Texto
    private lateinit var textToSpeech: TextToSpeech

    //Lista de reproduccion para la voz
    private val textToSpeechQueue = LinkedList<String>()

    //PARA LA RUTA
    //Localizacion del A007
    protected val a_007 = LatLng(19.313294, -99.057665)

    private var start: String = ""
    private var end: String = ""

    var poly: Polyline? = null

    // Define la variable para el cliente de ubicación.
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        const val REQUEST_CODE_LOCATION = 0
    }


    private fun drawLineOnMap(startLocation: LatLng) {
        //val startPoint = LatLng(19.314497, -99.059159)  // Latitud y longitud del primer punto
        //val endPoint = LatLng(19.314161, -99.059105)  // Latitud y longitud del segundo punto
        val endPoint = a_007

        // Agregar marcadores para los puntos
        map.addMarker(MarkerOptions().position(startLocation).title("Ubicación actual"))
        map.addMarker(MarkerOptions().position(endPoint).title("Punto de destino"))

        // Crear una línea entre los puntos
        val line = map.addPolyline(PolylineOptions().add(startLocation, endPoint))

        // Mover la cámara para que ambos puntos sean visibles en el mapa
        //map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds(startLocation, endPoint), 100))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa el cliente de ubicación.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnCalculate = findViewById(R.id.button);

        //Cargamos el Mapa
        createMapFragment()

        //Inicializamos el textToSpeech
        textToSpeech = TextToSpeech(this, this)

        //createRute()

        btnCalculate.setOnClickListener {
            start = ""
            end = ""
            poly?.remove()
            poly = null
            if (::map.isInitialized) {
                ubicacionActual { currentLocation ->
                    if (currentLocation != null) {
                        drawLineOnMap(currentLocation)
                    } else {
                        // Handle the case where current location is not available.
                        // You can display a message or take appropriate action.
                    }
                    /*PARA MARCAR DESDE EL CLICK AL MAPA
                    map.setOnMapClickListener {
                        if (start.isEmpty()) {
                            start = "${it.longitude},${it.latitude}"
                        } else if (end.isEmpty()) {
                            end = "${it.longitude},${it.latitude}"
                            createRoute()
                        }
                    }*/
                }
            }
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this)
        }

    }

    private fun ubicacionActual(callback: (LatLng?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    // La ubicación actual está disponible en la variable "location".
                    val ubiactual = LatLng(location.latitude, location.longitude)
                    callback(ubiactual)
                }
            }
        } else {
            // Si no tienes permiso para acceder a la ubicación, deberías solicitarlo primero.
            // Puedes mostrar un diálogo de solicitud de permiso o usar la API de solicitud de permiso.
            callback(null)
        }
    }

    //Creando el mapa
    private fun createMapFragment() {
        val mapFragment: SupportMapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    //Ejecutando el mapa
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        createMarker()
        map.setOnMyLocationButtonClickListener(this)
        enableLocation()
    }

    //Para hacer un zoom a la universidad
    private fun createMarker() {
        //Variable con las coordenadas de la UNI Edificio A
        val coordinates = LatLng(19.313355, -99.057776)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(coordinates, 18f), 4000, null
        )
    }

    //--------------------------------------- MAPA -------------------------------------------------//
    //Comprobar los persmisos de ubicacion
    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun enableLocation() {
        //Si el mapa no esta creado entonces salte
        if (!::map.isInitialized)
            return
        if (isLocationPermissionGranted()) {//Si ya ha aceptado el permiso
            map.isMyLocationEnabled = true
        } else {//Si no
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            textoAVoz("Ve a Ajustes y concede los permisos a MiUACMSLT")
            Toast.makeText(
                this,
                "Ve a Ajustes y concede los permisos a MiUACMSLT",
                Toast.LENGTH_LONG
            ).show()

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_LOCATION -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
            } else {
                Toast.makeText(
                    this,
                    "Para activar la localizacion ve a ajustes y acepta los permisos",
                    Toast.LENGTH_LONG
                ).show()
                textoAVoz("Para activar la localizacion ve a ajustes y acepta los permisos")
            }
            else -> {}
        }
    }

    //Comprobar que los permisos sigan activos si movio algo
    override fun onResumeFragments() {
        super.onResumeFragments()
        if (!::map.isInitialized)
            return
        if (!isLocationPermissionGranted()) {
            map.isMyLocationEnabled = false
            Toast.makeText(
                this,
                "Para activar la localizacion ve a ajustes y acepta los permisos",
                Toast.LENGTH_LONG
            ).show()
            textoAVoz("Para activar la localizacion ve a ajustes y acepta los permisos")
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "Localizando...", Toast.LENGTH_SHORT).show()
        textoAVoz("Localizando...")
        //Hacer metodo para que si estoy dentro del area de la UACM me ubique y me diga en donde estoy
        return false
    }

    //Metodo para Retrofit
    private fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun pideRuta() {

    }

    //Para crear una ruta
    private fun createRoute() {
        CoroutineScope(Dispatchers.IO).launch {//Ya no estamos en el hilo principal y lo lanzamos
            //Interfaz de ApiService
            //le pasamos la API, donde empieza y donde termina la ruta
            val call = getRetrofit().create(ApiService::class.java)
                .getRoute("5b3ce3597851110001cf6248807105cbed18485094b563a37cb2bf01", start, end)
            if (call.isSuccessful) {//Si tenemos todo lo necesario entonces nos pinta la ruta
                drawRoute(call.body())
            } else {
                Log.i("aris", "KO")
                //print("\n\n\n-----------------------------ERRRRRRROOOOOR EN RUTA ------------------------------------------------\n\n\n")
            }
        }
    }

    private fun drawRoute(routeResponse: RouteResponse?) {
        val polyLineOptions = PolylineOptions()
        routeResponse?.features?.first()?.geometry?.coordinates?.forEach {
            polyLineOptions.add(LatLng(it[1], it[0]))
        }
        runOnUiThread {
            poly = map.addPolyline(polyLineOptions)
        }
    }


    //--------------------------------------- MAPA -------------------------------------------------//


    //##################################  TEXTO - VOZ ##################################################//
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("es", "LAT")
            val result = textToSpeech.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // El idioma no es compatible.
            } else {
                // La inicialización fue exitosa, ahora podemos dar la bienvenida.
            }
        } else {
            // Error en la inicialización.
        }
    }

    override fun onDestroy() {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        super.onDestroy()
    }

    fun textoAVoz(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    //##################################  TEXTO - VOZ ##################################################//
}
