package com.example.weatherapp.Activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.weatherapp.Models.WeatherModel
import com.example.weatherapp.R
import com.example.weatherapp.Utilites.ApiUtilities
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Response
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding  // Binding for the layout
    private lateinit var currentLocation: Location // Variable to store the current location
    private lateinit var fusedLocationProvider: FusedLocationProviderClient // To get the current location
    private val LOCATION_REQUEST_CODE = 101  // Code to request location permission
    private val apiKey = "e8b68578f5768a1d42dd5dbc764e7f5b"  // API key for accessing weather data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initializing DataBinding
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)  // Get FusedLocationProviderClient instance
        getcurrentLocation()  // Get the current location when the activity starts

        // Listen to city search input (when user presses search)
        binding.citySearch.setOnEditorActionListener { textview, i, keyevent ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                // Fetch weather data for the city entered by the user
                getCityWeather(binding.citySearch.text.toString())

                // Hide the keyboard after search
                val view = this.currentFocus
                if (view != null) {
                    val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    binding.citySearch.clearFocus()
                }
                return@setOnEditorActionListener true
            } else {
                return@setOnEditorActionListener false
            }
        }

        // When current location button is clicked, fetch current location
        binding.currentLocation.setOnClickListener {
            getcurrentLocation()
        }
    }

    // Method to fetch weather data for a specific city
    private fun getCityWeather(city: String) {
        binding.progressBar.visibility = View.VISIBLE // Show progress bar while data is loading

        // Make a network call to get weather data for the specified city
        ApiUtilities.getApiInterface()?.getCityWeatherData(city, apiKey)?.enqueue(object : retrofit2.Callback<WeatherModel> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<WeatherModel>, response: Response<WeatherModel>) {
                if (response.isSuccessful) {
                    binding.progressBar.visibility = View.GONE  // Hide progress bar once data is received

                    response.body()?.let {
                        setData(it)  // Update UI with the weather data
                    }
                } else {
                    Toast.makeText(this@MainActivity, "No City Found", Toast.LENGTH_SHORT).show() // Show error if city is not found
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                // Handle failure (network error, etc.)
            }
        })
    }

    // Method to fetch weather data for the current location
    private fun fetchCurrentLocationWeather(latitude: String, longitude: String) {
        ApiUtilities.getApiInterface()?.getCurrentWeatherData(latitude, longitude, apiKey)
            ?.enqueue(object : retrofit2.Callback<WeatherModel> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(call: Call<WeatherModel>, response: Response<WeatherModel>) {
                    if (response.isSuccessful) {
                        binding.progressBar.visibility = View.GONE  // Hide progress bar once data is received
                        response.body()?.let {
                            setData(it)  // Update UI with the weather data
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                    // Handle failure (network error, etc.)
                }
            })
    }

    // Method to get current location using the FusedLocationProviderClient
    private fun getcurrentLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                // If location permissions are granted, fetch the last known location
                if (ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()  // Request permission if not granted
                    return
                }
                fusedLocationProvider.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location  // Store the current location
                        binding.progressBar.visibility = View.VISIBLE  // Show progress bar while fetching weather data
                        fetchCurrentLocationWeather(location.latitude.toString(), location.longitude.toString())  // Fetch weather data based on location
                    }
                }
            } else {
                // If location is disabled, redirect to location settings
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermission()  // Request permission if not granted
        }
    }

    // Method to request location permissions
    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }

    // Method to check if location is enabled (GPS or Network Provider)
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Method to check if location permissions are granted
    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Handle the result of the location permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getcurrentLocation()  // Retry getting location if permission is granted
            }
        }
    }

    // Method to update the UI with the weather data
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setData(body: WeatherModel) {
        binding.apply {
            // Format and set the current date and time
            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())
            dateTime.text = currentDate.toString()

            // Set various weather data to the UI components
            maxTemp.text = "Max " + k2c(body.main.temp_max) + "°"
            minTemp.text = "Min " + k2c(body.main.temp_min) + "°"
            temp.text = "" + k2c(body.main.temp) + "°"
            weatherTitle.text = body.weather[0].main
            sunriseValue.text = ts2td(body.sys.sunrise.toLong())
            sunsetValue.text = ts2td(body.sys.sunset.toLong())
            pressureValue.text = body.main.pressure.toString()
            humidityValue.text = body.main.humidity.toString() + "%"
            tempFValue.text = "" + k2c(body.main.temp).times(1.8).plus(32).roundToInt()
            citySearch.setText(body.name)
            feelsLike.text = "" + k2c(body.main.feels_like) + "°"
            windValue.text = body.wind.speed.toString() + " m/s"
            groundValue.text = body.main.grnd_level.toString()
            seaValue.text = body.main.sea_level.toString()
            countryValue.text = body.sys.country
        }

        updateUI(body.weather[0].id)  // Update UI based on weather conditions
    }

    // Method to update the UI background and icons based on weather condition code
    private fun updateUI(id: Int) {
        binding.apply {
            when (id) {
                in 200..232 -> {  // Thunderstorm
                    weatherImg.setImageResource(R.drawable.ic_storm_weather)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.thunderstrom_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.thunderstrom_bg)
                }
                in 300..321 -> {  // Drizzle
                    weatherImg.setImageResource(R.drawable.ic_few_clouds)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.drizzle_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.drizzle_bg)
                }
                in 500..531 -> {  // Rain
                    weatherImg.setImageResource(R.drawable.ic_rainy_weather)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.rain_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.rain_bg)
                }
                in 600..622 -> {  // Snow
                    weatherImg.setImageResource(R.drawable.ic_snow_weather)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.snow_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.snow_bg)
                }
                in 701..781 -> {  // Atmospheric conditions (fog, mist, smoke, etc.)
                    weatherImg.setImageResource(R.drawable.ic_broken_clouds)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.atmosphere_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.atmosphere_bg)
                }
                800 -> {  // Clear sky
                    weatherImg.setImageResource(R.drawable.ic_clear_day)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.clear_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.clear_bg)
                }
                in 801..804 -> {  // Clouds
                    weatherImg.setImageResource(R.drawable.ic_cloudy_weather)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.clouds_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.clouds_bg)
                }
                else -> {  // Unknown weather condition
                    weatherImg.setImageResource(R.drawable.ic_unknown)
                    mainLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.unknown_bg)
                    optionsLayout.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.unknown_bg)
                }
            }
        }
    }

    // Method to convert timestamp (sunrise/sunset) to human-readable time
    @RequiresApi(Build.VERSION_CODES.O)
    private fun ts2td(ts: Long): String {
        val localTime = ts.let {
            Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }
        return localTime.toString()
    }

    // Method to convert Kelvin to Celsius
    private fun k2c(t: Double): Double {
        var intTemp = t
        intTemp = intTemp.minus(273)  // Convert from Kelvin to Celsius
        return intTemp.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()  // Round to 1 decimal place
    }
}
