package com.example.shakecalc

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    // UI Components
    private lateinit var tvResult: TextView

    // Logic Variables
    private var lastNumeric: Boolean = false
    private var stateError: Boolean = false
    private var lastDot: Boolean = false

    // Hardware: Vibration
    private lateinit var vibrator: Vibrator

    // Hardware: Sensor (Shake)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    // [BARU] Variabel untuk penyimpanan data
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "ShakeCalcPrefs"
    private val KEY_RESULT = "last_result"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = findViewById(R.id.tvResult)

        // Init Vibration Service
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Init Sensor Service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        // [BARU] Inisialisasi SharedPreferences & Load Data Terakhir
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadData()
    }

    /* -----------------------------------------------------
       [BARU] FITUR MANAJEMEN DATA (SIMPAN & LOAD)
       ----------------------------------------------------- */
    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_RESULT, tvResult.text.toString())
        editor.apply()
    }

    private fun loadData() {
        val savedResult = sharedPreferences.getString(KEY_RESULT, "0")
        tvResult.text = savedResult

        // Perbarui status logika agar bisa lanjut menghitung
        if (savedResult != "0" && savedResult != "Error") {
            lastNumeric = true
        }
    }

    /* -----------------------------------------------------
       HARDWARE INTERACTION & LIFECYCLE
       ----------------------------------------------------- */
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)

        // [BARU] Simpan data otomatis saat aplikasi dipause/ditutup/pindah layar
        saveData()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration

            if (delta > 12f) {
                clearAll()
                triggerVibration()
                Toast.makeText(this, "Cleared by Shake!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    private fun triggerVibration() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    /* -----------------------------------------------------
       CALCULATOR LOGIC
       ----------------------------------------------------- */
    fun onDigitClick(view: View) {
        triggerVibration()
        if (stateError) {
            tvResult.text = (view as Button).text
            stateError = false
        } else {
            if (tvResult.text.toString() == "0" && (view as Button).text != ".") {
                tvResult.text = view.text
            } else {
                tvResult.append((view as Button).text)
            }
        }
        lastNumeric = true
    }

    fun onOperatorClick(view: View) {
        triggerVibration()
        if (lastNumeric && !stateError) {
            tvResult.append((view as Button).text)
            lastNumeric = false
            lastDot = false
        }
    }

    fun onClearClick(view: View) {
        triggerVibration()
        clearAll()
    }

    private fun clearAll() {
        tvResult.text = "0"
        lastNumeric = false
        stateError = false
        lastDot = false
        saveData() // [BARU] Simpan status 0
    }

    fun onBackspaceClick(view: View) {
        triggerVibration()
        val text = tvResult.text.toString()
        if (text.isNotEmpty() && text != "0") {
            tvResult.text = text.substring(0, text.length - 1)
            if (tvResult.text.isEmpty()) {
                tvResult.text = "0"
            }
        }
        lastNumeric = tvResult.text.last().isDigit()
    }

    fun onEqualClick(view: View) {
        triggerVibration()
        if (lastNumeric && !stateError) {
            val txt = tvResult.text.toString()
            try {
                val result = evaluateExpression(txt)
                tvResult.text = if (result % 1.0 == 0.0) {
                    result.toInt().toString()
                } else {
                    result.toString()
                }
                lastDot = true
                saveData() // [BARU] Simpan hasil hitungan
            } catch (ex: Exception) {
                tvResult.text = "Error"
                stateError = true
                lastNumeric = false
            }
        }
    }

    private fun evaluateExpression(expression: String): Double {
        val tokens = expression.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
        if (tokens.size < 3) return tokens[0].toDouble()

        var result = tokens[0].toDouble()
        var i = 1
        while (i < tokens.size) {
            val operator = tokens[i]
            val nextVal = tokens[i+1].toDouble()
            result = when (operator) {
                "+" -> result + nextVal
                "-" -> result - nextVal
                "*" -> result * nextVal
                "/" -> result / nextVal
                else -> result
            }
            i += 2
        }
        return result
    }
}