package com.example.miuacmslt

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import java.util.*

class Principal : AppCompatActivity(), TextToSpeech.OnInitListener{

    private lateinit var textToSpeech: TextToSpeech
    private val textQueue = LinkedList<String>()

    private lateinit var btnAcceso:Button
    private lateinit var btnTutorial:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_principal)

        textQueue.add("Bienvenido a Mi UACM-SLT")//Bienvenida
        textQueue.add("Si deseas ver nuestro tutorial para tener una mejor experiencia en el uso de nuestra app," +
                " pulsa el botón que está en la parte superior de esta pantalla." +
                "De lo contrario presione el boton que aparece en la parte inferior de esta pantalla " +
                "para acceder a la funcionalidad principal de nuestra app")//Informacion

        btnAcceso = findViewById(R.id.btnSkip)
        btnTutorial = findViewById(R.id.btnTutorial)

        textToSpeech = TextToSpeech(this, this)

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Acciones cuando comienza la reproducción
            }

            override fun onDone(utteranceId: String) {
                // Cuando se completa la reproducción, iniciar el siguiente texto si hay más en la cola
                if (textQueue.isNotEmpty()) {
                    val nextText = textQueue.poll()
                    textoAVoz(nextText)
                } else {
                    // Si no hay más elementos en la cola, la liberamos.
                    textQueue.clear()
                }
                textQueue.clear()
            }

            override fun onError(utteranceId: String) {
                // Manejar errores si es necesario
            }
        })
        //Si preciona el boton entonces le doy acceso al sistema
        btnAcceso.setOnClickListener{
            // Detener la reproducción de voz actual
            textToSpeech.stop()
            // Verifica si hay elementos restantes en la cola antes de abrir una nueva actividad
            textQueue.clear()
            llamaIntent(this,MainActivity::class.java)
        }
        /*btnTutorial{ llamaIntent(this,Tutorial::class.java) }*/

    }

    fun textoAVoz(text: String) {
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = UUID.randomUUID().toString()
        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params)
    }

    fun reproduceTexto(text: String){
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("es", "LAT")
            val result = textToSpeech.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // El idioma no es compatible.
            } else {
                // La inicialización fue exitosa, ahora podemos dar la bienvenida.

                iniciarReproduccionSecuencial()
            }
        } else {
            // Error en la inicialización.
        }
    }

    private fun iniciarReproduccionSecuencial() {
        if (textQueue.isNotEmpty()) {
            val text = textQueue.poll()
            textoAVoz(text)
        }
    }

    private fun llamaIntent(activity: AppCompatActivity, actividadDestino: Class<*>) {
        val intent = Intent(activity, actividadDestino)
        activity.startActivity(intent)
    }
}