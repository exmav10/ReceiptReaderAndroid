package com.example.googlemlkit

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.Toast
import android.util.Log
import androidx.core.net.toFile
import com.google.firebase.ml.vision.text.FirebaseVisionText
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import kotlin.contracts.contract


class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE: Int = 1000
    private val IMAGE_CAPTURE_CODE: Int = 1001
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        buttonCamera.setOnClickListener{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    // permission was not enabled
                    val permission = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    requestPermissions(permission, PERMISSION_CODE)
                } else {
                    openCamera()
                }
            } else {
                openCamera()
            }
        }
        //this.getServiceTest()
    }

    // Müşteri no
    private fun findSubscriptionNumber(visionText: FirebaseVisionText): String {
        val resultText = getElements(visionText)
        for (i in 0..resultText.size - 2) {
            if (resultText[i] == "müsteri" || resultText[i] == "musteri" || resultText[i] == "musterı" || resultText[i] == "müsterı" || resultText[i] == "müşteri" || resultText[i] == "muşterı" || resultText[i] == "müşterı" || resultText[i] == "muşteri") {
                if (resultText[i + 1] == "no") {
                    for (j in i..resultText.size - 2 ) {
                        if (resultText[j].count() > 8) {
                            return resultText[j]
                        }
                    }
                }
            }
        }
        return ""
    }

    // Tutar
    private fun findFee(visionText: FirebaseVisionText): String {
        val resultText = getElements(visionText)
        for (i in 0..resultText.size - 3) {
            if (resultText[i] == "fatura" && ( resultText[i+1] == "tutar" || resultText[i+1] == "tutarı" || resultText[i+1]== "tutari")) {
                return resultText[i+2] + " TL"
            }
        }
        for (i in 0..resultText.size - 3) {
            if ((resultText[i] == "ödenecek" || resultText[i] == "odenecek") && ( resultText[i+1] == "tutar")) {
                return resultText[i+3] + " TL"
            }
        }

        return ""
    }


    // Sözleşme Hesap No
    private fun findContractNo(firebaseVisionText: FirebaseVisionText): String {
        val stringMatrix = getTextLinebyLine(firebaseVisionText)
        for (stringArrayidx in 0..stringMatrix.count()-2) {
            val stringArray = stringMatrix[stringArrayidx]
            for (elementidx in 0..stringArray.count()-1) {
                if (stringArray[elementidx].contains("hesap") && stringArray[elementidx + 1].contains("no")) {
                    return stringMatrix[stringArrayidx+1][0]
                }
            }
        }
        return ""
    }


    private fun getTextLinebyLine(visionText: FirebaseVisionText): MutableList<MutableList<String>> {
        var stringMatrix: MutableList<MutableList<String>> = mutableListOf()
        for (block in visionText.textBlocks) {
            for (lineidx in 0..block.lines.count()-1) {
                val line = block.lines[lineidx]
                var stringArray = mutableListOf<String>()
                for (element in line.elements) {
                    stringArray.add(element.text.toLowerCase())
                }
                stringMatrix.add(stringArray)
            }
        }
        return stringMatrix
    }

    private fun getElements(visionText: FirebaseVisionText): MutableList<String>{
        var stringMatrix: MutableList<String> = mutableListOf()
        for (block in visionText.textBlocks) {
            for (lineidx in 0..block.lines.count()-1) {
                val line = block.lines[lineidx]
                for (element in line.elements) {
                    stringMatrix.add(element.text.toLowerCase())
                }
            }
        }
        return stringMatrix
    }

    private fun openCamera(){
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Receipt Photo")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK){
            var uri = imageUri?.let {
                imageView.setImageURI(it)
                this.contractTextView.text = ""
                runGoogleTextRecognition(it, {
                    this.contractTextView.text = "Tutar:" + this.findFee(it) + "\n" +
                            "Hesap No: " + this.findContractNo(it) + "\n" +
                            "Müşteri No: " +  findSubscriptionNumber(it)
                })
                //uploadImageToServer(it)
            }
        }
    }

    private fun runGoogleTextRecognition(uri: Uri, callback: (FirebaseVisionText) -> Unit){
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer
        val firebaseImage = uri.let { FirebaseVisionImage.fromFilePath(this, uri) }
        val returnText: MutableList<String> = mutableListOf()
        val result = firebaseImage?.let {
            detector?.processImage(it)?.addOnSuccessListener { firebaseVisionText ->
                var resultText: String = ""
                for (block in firebaseVisionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            returnText.add(element.text.toLowerCase())
                            resultText = resultText + " " + element.text
                        }
                    }
                }
                callback(firebaseVisionText)
            }?.addOnFailureListener {
                println("ENTERED ERROR ${it.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            PERMISSION_CODE -> {
                if(grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission from popup was granted
                    openCamera()
                }else{
                    // permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadImageToServer(uri: Uri) {
        val retrofit: Retrofit = NetworkClient().getRetrofitClient()
        val uploadAPIs: UploadAPIs = retrofit.create(UploadAPIs :: class.java)
        //val file = File("/document/raw:/storage/emulated/0/Download/deneme.png")
        val fileReqBody: RequestBody = RequestBody.create(MediaType.parse("image/*"), File(uri.path))
        val part: MultipartBody.Part = MultipartBody.Part.createFormData("file", File(uri.path).name, fileReqBody)
        val description = RequestBody.create(MediaType.parse("text/plain"), "image-type")

        val call = uploadAPIs.uploadImage(part, description)
        call.enqueue(object : Callback<ImageResponse> {
            override fun onFailure(call: Call<ImageResponse>, t: Throwable) {
                contractTextView.text = "IMAGE: " + t.localizedMessage
            }

            override fun onResponse(call: Call<ImageResponse>, response: Response<ImageResponse>) {
                contractTextView.text = "IMAGE: " + response.body()?.message
            }

        })
    }

    private fun getServiceTest() {
        val retrofit: Retrofit = NetworkClient().getRetrofitClient()
        val uploadAPIs: UploadAPIs = retrofit.create(UploadAPIs :: class.java)
        val call = uploadAPIs.checkStatus()
        call.enqueue(object : Callback<StatusResponse> {
            override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                contractTextView.text = "TEST: " +  t.localizedMessage
            }

            override fun onResponse(
                call: Call<StatusResponse>,
                response: Response<StatusResponse>
            ) {
                contractTextView.text = "TEST: " + response.body()?.status
            }

        })

    }

}


