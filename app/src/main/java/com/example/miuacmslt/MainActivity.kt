package com.example.miuacmslt

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
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
    TextToSpeech.OnInitListener, View.OnClickListener {
    //Para el Mapa
    private lateinit var map: GoogleMap
    private lateinit var btnEscuchar: Button //Es el boton que dice escuchar
    private lateinit var btnRuta: Button
    //private lateinit var btnEscuchar:Button
    private var rutaTrazada:Boolean = false
    //Para saber si esta lanzada la escucha de voz
    private var preguntaEnProgreso: Boolean = false


    //Para Texto a Voz
    private lateinit var textToSpeech: TextToSpeech

    //Voz a Texto
    private val RQ_SPEECH_REC = 102

    //Lista de reproduccion para la voz
    private val textToSpeechQueue = LinkedList<String>()

    //PARA LA RUTA
    //Localizacion del A007
    protected val a_007 = LatLng(19.313294, -99.057665)
    private var salonDestino: String = ""
    private var respuesta:String = " "
    private lateinit var destino : LatLng

    val salonesDisponibles = arrayListOf(
        arrayListOf("a004", 19.313343, -99.057829),
        arrayListOf("a005", 19.313335, -99.057790),
        arrayListOf("a006", 19.313321, -99.057761),
        arrayListOf("a007", 19.313311, -99.057717),
        arrayListOf("a008", 19.313290, -99.057668),
        arrayListOf("a009", 19.313284, -99.057619),
        arrayListOf("a010", 19.313271, -99.057570)
    )

    private var start: String = ""
    private var end: String = ""

    var poly: Polyline? = null

    // Define la variable para el cliente de ubicación.
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        const val REQUEST_CODE_LOCATION = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa el cliente de ubicación.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Inicializamos el textToSpeech
        textToSpeech = TextToSpeech(this, this)

        //Cargamos el Mapa
        createMapFragment()

        btnEscuchar = findViewById(R.id.btnEscuchar)
        btnRuta = findViewById(R.id.btnRuta)

        btnEscuchar.visibility = View.VISIBLE
        btnRuta.visibility = View.INVISIBLE

        btnEscuchar.setOnClickListener(this);
        btnRuta.setOnClickListener(this)
    }

    override
    fun onClick(view: View) {
        when (view.id) {
            R.id.btnEscuchar -> {
                start = ""
                end = ""
                //Si antes ya nos habia dado una ruta la limpiamos
                poly?.remove()
                poly = null
                //Primero escuchamos a que salon quiere ir

                //askSpeechInput("Dime a que Aula es a la que quieres llegar")
                //comprobamos si el salon que dijo esta en los disponibles
                respuesta="a004"
                var indiceEncontrado: Int = -1
                for ((indice, elemento) in salonesDisponibles.withIndex()) {
                    if (elemento[0] == respuesta) {
                        indiceEncontrado = indice
                        break
                    }
                }
                if (indiceEncontrado != -1) {//Si se encontro el salon entonces trazamos la ruta
                    trazarRuta(indiceEncontrado)
                } else {
                    print("El salon buscado no concuerda")
                }

            }

            R.id.btnRuta -> {
                textoAVoz("Deseas comenzar el viaje? Responde con Si o No")
                //askSpeechInput("Responde con Si o No")
                Log.d("MiTag", "-------------------$respuesta-----------------")
                respuesta="Si"
                if (respuesta.equals("Si", ignoreCase = true)){
                    //Comenzaremos el viaje
                    comenzarViaje()
                }
                if(respuesta.equals("No", ignoreCase = true)){
                    btnRuta.visibility = View.INVISIBLE
                    btnEscuchar.visibility = View.VISIBLE
                    //Elimino la linea y los marcadores
                    poly?.remove()
                    map.clear()
                }
                Log.d("MiTag", "-------------------$respuesta-----------------")
            }
        }
    }

    private fun comenzarViaje() {
        ubicacionActual { currentLocation ->
            if (currentLocation != null) {
                val distancia = calcularDistancia().calculaDistancia(currentLocation, destino)

                if (distancia >= 3) {
                    // Informa al usuario la distancia cada 3 metros
                    textoAVoz("Estás a una distancia de: ${distancia.toInt()} metros")
                }

                if (distancia > 0) {
                    // Espera un tiempo antes de verificar nuevamente (por ejemplo, 5 segundos)
                    Thread.sleep(5000)
                    comenzarViaje() // Llamada recursiva para verificar la ubicación y distancia nuevamente
                } else {
                    // Has llegado a tu destino
                    textoAVoz("Has llegado a tu destino.")
                }
            }
        }
    }


    private fun trazarRuta(indiceEncontrado:Int) {
        salonDestino=respuesta
        //respuesta = "alan"//Limpiamos respuesta
        //salonDestino = "a004"
        ubicacionActual { currentLocation -> //Obtenemos la ubicacion actual
            if (currentLocation != null) {//Si la ubicacion actual no es nula entonces procedemos
                //if (verificaDistancia(currentLocation) == true) {
                    if (indiceEncontrado != null) {//Si esta dentro de los salones disponibles entonces traza la ruta
                        textoAVoz("Se trazara la ruta al salon que quieres llegar")
                        if (::map.isInitialized) {//Si el mapa ya esta inicializado entonces trazaremos la ruta
                            //val calculaD = calcularDistancia()

                            val elementoEncontrado =
                                salonesDisponibles[indiceEncontrado]//Guardamos los datos del salon: cadena,latitud,longitud Destino
                            //val destino =
                                destino =
                                LatLng(
                                    elementoEncontrado[1] as Double,
                                    elementoEncontrado[2] as Double
                                )
                            val distancia: Double =
                                calcularDistancia().calculaDistancia(currentLocation, destino)
                            textoAVoz("Estas a una distancia de: ${distancia.toInt()} metros")
                            //Aqui pinto la linea
                            drawLineOnMap(currentLocation, destino)
                            //Si ya se pinto la linea entonces
                            if(rutaTrazada==true){
                                //Muestro el boton de comenzar viaje
                                btnEscuchar.visibility = View.INVISIBLE
                                btnRuta.visibility = View.VISIBLE
                                //askSpeechInput("Dime Si o No para comenzar")
                            }

                            /*
                            do{
                                textoAVoz("¿Deseas comenzar tu viaje?")
                            }while(!textToSpeech.isSpeaking)
                            askSpeechInput("Dime Si o No para comenzar")
                            if (respuesta=="No"){
                                btnCalculate.visibility = View.GONE
                            }else{//Comenzamos con el viaje

                            }*/
                        } else {
                            Toast.makeText(
                                this,
                                "Ocurrio un error.... Intentalo mas tarde",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        val mapFragment =
                            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                        mapFragment.getMapAsync(this)
                    } else {
                        if (salonDestino.equals("alan")) {//Tenemos que ver si es la primera vez que selecciona un salon
                            print("ERROR")
                        } else {
                            textoAVoz("El salon que seleccionaste no esta disponible, intenta nuevamente")
                            Toast.makeText(
                                this,
                                "El salon que seleccionaste no esta disponible, intenta nuevamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                //}
            }//Fin si de la ubicacion null
        }
    }

    //Si estamos lejos de los salones entonces es porque aun no creamos una ruta para esas ubicaciones
    private fun verificaDistancia(actual: LatLng): Boolean {
        var regreso: Boolean = false
        //Si estoy a una distancia mayor que 3 metros entonces no nos deja crear nuestro recorrido
        if(calcularDistancia().calculaDistancia(actual,LatLng(19.313349, -99.057856))<3){
            regreso=true
        }
        return regreso
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


    //---------------------------------- VOZ A TEXTO ----------------------------------------------//
    private fun askSpeechInput(textoAVoz: String) {
        if (!preguntaEnProgreso) {
            preguntaEnProgreso = true // Establece la bandera como verdadera para indicar una pregunta en progreso.

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(this, "El Dictado de voz no está disponible", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                i.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-419") // Para Spanish Latin
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, textoAVoz)
                startActivityForResult(i, RQ_SPEECH_REC) // Lanza la actividad de reconocimiento de voz
            }
        } else {
            // Muestra un mensaje o realiza otra acción si ya hay una pregunta en progreso.
            //textoAVoz("Por favor, espere a que se complete la pregunta anterior.")
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RQ_SPEECH_REC && resultCode == Activity.RESULT_OK) {
            val result: ArrayList<String>? =
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (result != null && result.isNotEmpty()) {
                // salonDestino = result?.get(0).toString()
                respuesta = result?.get(0).toString()

            } else {
                runOnUiThread {
                    Toast.makeText(this, "Ocurrió un error", Toast.LENGTH_SHORT).show()
                }
            }
        }
        preguntaEnProgreso = false // Establece la bandera como false después de recibir la respuesta.
    }


    //---------------------------------- VOZ A TEXTO ----------------------------------------------//

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

    //Dibujar una Linea dados los 2 puntos en el mapa
    private fun drawLineOnMap(startLocation: LatLng, endPoint: LatLng) {
        //Borramos si hay una linea Anterior
        poly?.remove()
        //Le digo cual selecciono el usuario
        //val endPoint = a_007

        // Agregar marcadores para los puntos
        map.addMarker(MarkerOptions().position(startLocation).title("Ubicación actual"))
        map.addMarker(MarkerOptions().position(endPoint).title("Punto de destino"))

        // Dibujar la nueva línea
        val polylineOptions = PolylineOptions()
            .add(startLocation, endPoint)
            .color(Color.RED)
            .width(5f)

        poly = map.addPolyline(polylineOptions)
        // Marcar que la ruta está trazada
        rutaTrazada = true
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
        val rate = 1.3f
        textToSpeech.setSpeechRate(rate)
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    //##################################  TEXTO - VOZ ##################################################//
}
