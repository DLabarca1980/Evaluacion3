package com.example.evaluacion3

import android.Manifest 
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime

enum class Pantalla {           // MENU pantallas
    FORM,
    FOTO
}

class CameraAppViewModel : ViewModel() {
    val latitud   = mutableStateOf(0.0)
    val longitud  = mutableStateOf(0.0)

    val pantalla = mutableStateOf(Pantalla.FORM)

    var onPermisoCamaraOK    : () -> Unit = {}
    var onPermisoUbicacionOK : () -> Unit = {}

    // Lanzador de permisos
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null

    fun cambiarPantallaFoto() { pantalla.value  = Pantalla.FOTO }
    fun cambiarPantallaForm() { pantalla.value  = Pantalla.FORM }
}

class FormRecepcionVM : ViewModel() {
    val ubicacion = mutableStateOf("")
    val latitud   = mutableStateOf(0.0)
    val longitud  = mutableStateOf(0.0)
    val fotoUbicacion = mutableStateOf<Uri?>(null)
}

class MainActivity : ComponentActivity() {
    val cameraAppVM:CameraAppViewModel by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val lanzadorPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions() )
    {
        when {
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION]) ?: false or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                Log.v("Callback RequestMultiplePermissions", "Permiso ubicacion otorgado")
                cameraAppVM.onPermisoUbicacionOK()
            }
            else -> {

            }
        }
    }
   private fun setupCamara() {
       cameraController = LifecycleCameraController(this)
       cameraController.bindToLifecycle(this)
       cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
   }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVM.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)

        }
    }
}

fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime.now().toString().replace(Regex("[T:.-]"),
"").substring(0, 14)

//    fun crearArchivoImagenPublica(contexto: Context):File = File {
//    val nombreImagen  = generarNombreSegunFechaHastaSegundo() + ".jpeg"
//
//    // Obtiene el directorio publico de imagenes
//    val directorioImagenes = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
//
//    // Crea el directorio si no existe
//    if (!directorioImagenes.exists() ) {
//        directorioImagenes.mkdir()
//    }
//    // Crea el archivo de imagen
//    return@File File(directorioImagenes, nombreImagen)
fun crearArchivoImagenPrivada(contexto: Context):File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),         // getExternalStoragePublicDirectory
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)
fun uri2imageBitmap(uri: Uri, contexto: Context) = BitmapFactory.decodeStream(
    contexto.contentResolver.openInputStream(uri)
).asImageBitmap()


fun capturarFoto(
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onImagenGuardadaOK: (uri:Uri) -> Unit
) {
    val opciones = OutputFileOptions.Builder(archivo).build()

    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults?.savedUri?.also {
                    Log.v("capturarFoto()::onImageSaved", "Foto guardada en " + "${it.toString()}"
                    )
                    onImagenGuardadaOK(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CapturarFoto::OnImageSavedCallBack::onError", exception.message?:"Error")
            }
        }
    )
}

class SinPermisoException(mensaje:String): Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOK:(location: android.location.Location) ->Unit):Unit {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOK(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para la ubicacion")
    }
}

@Composable
fun AppUI(cameraController: CameraController){
    val contexto = LocalContext.current

    val formRecepcionVM : CameraAppViewModel = viewModel()
    val cameraAppViewModel : CameraAppViewModel  = viewModel()

    when (cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRecepcionVM,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnclick = {
                   getUbicacion(contexto) {
                       formRecepcionVM.latitud.value = it.latitude
                       formRecepcionVM.longitud.value = it.longitude
                   }
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
                }
    )
}
    Pantalla.FOTO -> {
        PantallaFotoUI(formRecepcionVM, cameraAppViewModel, cameraController)
    } else -> run {
        Log.v("AppUI()", "when else, no deberia entrar aqui")
        }
    }
}

@Composable
fun PantallaFormUI(
    formRecepcionVM : CameraAppViewModel,
    tomarFotoOnClick:() -> Unit = {},
    actualizarUbicacionOnclick:() -> Unit = {}
) {
    val contexto = LocalContext.current
    Column (
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        TextField(
            label = { Text("latitud")},                // DECIA RECEPTOR
            value = formRecepcionVM.latitud.value.toString(),
            onValueChange = { newValue -> formRecepcionVM.latitud.value = newValue.toDoubleOrNull() ?: formRecepcionVM.latitud.value},
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        )
        TextField(  label = { Text("Longitud")},
                    value = formRecepcionVM.longitud.value.toString(),
                    onValueChange = { newValue -> formRecepcionVM.longitud.value = newValue.toDoubleOrNull() ?: formRecepcionVM.longitud.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
//        Text("Fotografia de la ubicación")    // decia esto Text("Fotografia de la recepcion de la encomienda: ")
//        Button(onClick = {
//            tomarFotoOnClick()
        )
        Button(onClick = tomarFotoOnClick) {
            Text(" Tomar Fotografía")
        }
        formRecepcionVM.fotoUbicacion.value?.also {
            Box( Modifier.size(200.dp, 100.dp) ) {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(it, contexto)),
                    contentDescription = "Imagen "
                )
            }
        }
        Text("La ubicacion es : lat: ${formRecepcionVM.latitud.value} y",
            " long: ${formRecepcionVM.longitud.value}  ")
        Button(onClick = {
            actualizarUbicacionOnclick()

        }) {
            Text("Actualizar Ubicacion")
        }
        Spacer(Modifier.height(100.dp))
    }

}

@Composable
fun PantallaFotoUI(formRecepcionVM  : CameraAppViewModel,
                   appViewModel     : CameraAppViewModel,
                   cameraController : CameraController) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            PreviewView(it).apply { controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
        )
    Button(onClick = {
            capturarFoto(
                capturarFoto, crearArchivoImagenPrivada(contexto),
                contexto
            ) {
                formRecepcionVM.fotoUbicacion.value = it
            appViewModel.cambiarPantallaFoto()
            }

    }) {
        Text("Tomar Foto")
    }
}

@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue = contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()

            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)

            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}







//@Composable
//fun PantallaCamaraUI(
//    lanzadorPermisos: ActivityResultLauncher<Array<String>>,
//    cameraController: LifecycleCameraController
//) {
//    val contexto = LocalContext.current
//    val formRegistroVM:FormRecepcionVM = viewModel()
//    val appVM:AppWM                    = viewModel()
//
//    lanzadorPermisos.launch(arrayOf(android.Manifest.permission.CAMERA) )
//
//    AndroidView(
//        modifier = Modifier.fillMaxSize(),
//        factory = {
//            PreviewView(it).apply {
//                controller = cameraController
//            }
//        }
//    )
//    Button(onClick = {
//        capturarFoto(
//            cameraController,
//            crearArchivoImagenPrivada(contexto),
//            contexto
//        ) {
//          formRegistroVM.fotoUbicacion.value = it
//            appVM.pantallaActual.value = Pantalla.FORM
//        }
//
//    }) {
//        Text("Capturar Foto")
//
//    }
//}



