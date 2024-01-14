package com.example.evaluacion3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

enum class Pantalla {           // MENU pantallas
    FORM,
    CAMARA
}

class AppVM : ViewModel() {
    val pantallaActual = mutableStateOf(Pantalla.FORM)


    var onPermisoCamaraOk:() -> Unit = {}
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

        }
    }
}

