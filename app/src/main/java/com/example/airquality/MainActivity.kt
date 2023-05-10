package com.example.airquality


import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality.databinding.ActivityMainBinding
import com.example.airquality.retrofit.AirQualityResponse
import com.example.airquality.retrofit.AirQualityService
import com.example.airquality.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.create
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val PERMISSIONS_REQUEST_CODE = 100

    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>
    lateinit var locationProvider: LocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
        setRefreshButton()
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        val latitude: Double = locationProvider.getLocationLatitude()
        val longitude: Double = locationProvider.getLocationLongitude()

        if(latitude != 0.0 && longitude != 0.0) {
            val address = getCurrentAddress(latitude, longitude)

            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }

            getAirQualityData(latitude, longitude)

        } else {
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없습니다. 새로고침을 눌러주세요",Toast.LENGTH_LONG).show()
        }
    }

    private fun getAirQualityData(latitude: Double, longitude: Double) {
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        retrofitAPI.getAirQualityData(latitude.toString(), longitude.toString(), "13f19c0c-9bba-4c06-8c23-7ae6d7b4a7fc"
        ).enqueue(object : Callback<AirQualityResponse> {
            override fun onResponse(
                call: Call<AirQualityResponse>,
                response: Response<AirQualityResponse>
            ) {
                if(response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!", Toast.LENGTH_SHORT).show()
                    response.body()?.let { updateAirUI(it) }
                } else {
                    Toast.makeText(this@MainActivity, "업데이트에 실패하였습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

   private fun updateAirUI(airQualityData: AirQualityResponse) {
       val pollutionData = airQualityData.data.current.pollution

       binding.tvCount.text = pollutionData.aqius.toString()

       val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
       val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

       binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

       when(pollutionData.aqius) {
           in 0..50 -> {
               binding.tvTitle.text = "좋음"
               binding.imgBg.setImageResource(R.drawable.bg_good)
           }
           in 51..150 -> {
               binding.tvTitle.text = "보통"
               binding.imgBg.setImageResource(R.drawable.bg_soso)
           }
           in 151..200 -> {
               binding.tvTitle.text = "나쁨"
               binding.imgBg.setImageResource(R.drawable.bg_bad)
           }
           else -> {
               binding.tvTitle.text = "매우 나쁨"
               binding.imgBg.setImageResource(R.drawable.bg_worst)
           }
       }
   }

    fun getCurrentAddress(latitude: Double, longitude: Double) : Address? {
        val geocoder = Geocoder(this, Locale.getDefault())

        val addresses: List<Address>?

        addresses = try {
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch(ioException: IOException) {
            Toast.makeText(this,"지오코더 서비스 사용불가",Toast.LENGTH_LONG).show()
            return null
        } catch(illegalArgumentException: java.lang.IllegalArgumentException) {
            Toast.makeText(this,"잘못된 위도, 경도입니다.",Toast.LENGTH_LONG).show()
            return null
        }

        if(addresses == null || addresses.size == 0) {
            Toast.makeText(this,"주소가 발견되지 않았습니다.",Toast.LENGTH_LONG).show()
            return null
        }

        val address: Address = addresses[0]
        return address
    }

    private fun checkAllPermissions() {
        if (!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting();
        } else {
            isRunTimePermissionsGranted();
        }
    }

    fun isLocationServicesAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))

    }

    fun isRunTimePermissionsGranted() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {
            var checkResult = true

            for(result in grantResults) {
                if(result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }
            if(checkResult) {
                updateUI()
            } else {
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showDialogForLocationServiceSetting() {
        getGPSPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->

            if(result.resultCode == Activity.RESULT_OK) {
                if(isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted()
                } else {
                    Toast.makeText(
                        this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }
        }
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")

        builder.setMessage("위치 서비스가 꺼져 있습니다.")
        builder.setPositiveButton("설정",
            DialogInterface.OnClickListener {
                dialog, id ->
                val callGPSSettingIntent = Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                getGPSPermissionLauncher.launch(callGPSSettingIntent)
            })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener()
        { dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 GPS 설정 후 사용해주세요.", Toast.LENGTH_SHORT).show()
            finish()
        })
        builder.create().show()
    }
}