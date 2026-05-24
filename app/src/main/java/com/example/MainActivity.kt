package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login", modifier = modifier) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } },
                onAdminLogin = { navController.navigate("admin") }
            )
        }
        composable("home") {
            HomeScreen()
        }
        composable("admin") {
            AdminScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onLoginSuccess: () -> Unit, onAdminLogin: () -> Unit) {
    var keyInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context).keyDao() }
    
    // Obfuscated string: "ADM_KEYS"
    val _t = remember { intArrayOf(107, 102, 107, 125, 75, 107, 83, 121).map { (it xor 42).toChar() }.joinToString("") }
    
    // Obfuscated array for "KGADM"
    val _a = intArrayOf(97, 109, 107, 110, 103)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)
        ) {
            Text(
                text = "Acesso Restrito",
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Autentique-se para continuar",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray
            )
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
                showError = false
            },
            label = { Text("Chave de Autenticação", color = Color.Gray) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedLabelColor = Color.White
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )

        if (showError) {
            Text(
                text = errorMessage.ifEmpty { "Chave inválida." },
                color = Color(0xFFFF5252),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 8.dp, start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val trimmedKey = keyInput.trim().uppercase()
                val isAdmin = trimmedKey.length == _a.size && trimmedKey.withIndex().all { (it.value.code xor 42) == _a[it.index] }

                coroutineScope.launch {
                    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"

                    if (isAdmin) {
                        try {
                            val (authorized, errorMsg) = AdminService.verifyAndBindAdminDevice(deviceId)
                            if (authorized) {
                                onAdminLogin()
                            } else {
                                errorMessage = errorMsg ?: "Acesso negado: Dispositivo de Admin Inválido."
                                showError = true
                            }
                        } catch (e: Exception) {
                            errorMessage = "Erro inesperado: ${e.message}"
                            showError = true
                        }
                        return@launch
                    }

                    val keyData = db.getKey(trimmedKey)

                    if (keyData == null) {
                        errorMessage = "Chave não encontrada."
                        showError = true
                    } else if (keyData.isBanned) {
                        errorMessage = "Chave banida."
                        showError = true
                    } else if (keyData.deviceId != null && keyData.deviceId != deviceId) {
                        errorMessage = "Chave atrelada a outro dispositivo."
                        showError = true
                    } else {
                        // Bind device id if not binded
                        if (keyData.deviceId == null) {
                            db.updateKey(keyData.copy(deviceId = deviceId))
                        }
                        onLoginSuccess()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Entrar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
fun AdminScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context).keyDao() }
    val keys by db.getAllKeys().collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C2C2C))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Painel Administrador", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                coroutineScope.launch {
                    val newKey = UUID.randomUUID().toString().substring(0, 8).uppercase()
                    db.insertKey(AccessKey(keyValue = newKey))
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Gerar Chave", tint = Color.Green)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(keys) { key ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Chave: ${key.keyValue}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { clipboard.setText(AnnotatedString(key.keyValue)) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.LightGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Dispositivo: ${key.deviceId ?: "Não vinculado"}", color = Color.Gray, fontSize = 12.sp)
                        Text("Status: ${if (key.isBanned) "🔴 Banida" else "🟢 Ativa"}", color = if (key.isBanned) Color.Red else Color.Green, fontSize = 12.sp)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            // Unbind Device
                            if (key.deviceId != null) {
                                IconButton(onClick = { coroutineScope.launch { db.updateKey(key.copy(deviceId = null)) } }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Resetar HWID", tint = Color.Cyan)
                                }
                            }
                            // Ban / Unban
                            IconButton(onClick = { coroutineScope.launch { db.updateKey(key.copy(isBanned = !key.isBanned)) } }) {
                                Icon(
                                    if (key.isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                                    contentDescription = "Banir/Desbanir",
                                    tint = if (key.isBanned) Color.Green else Color.Red
                                )
                            }
                            // Delete
                            IconButton(onClick = { coroutineScope.launch { db.deleteKey(key) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val _creator = remember { intArrayOf(125, 88, 4, 124, 79, 88, 83).map { (it xor 42).toChar() }.joinToString("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), shape = MaterialTheme.shapes.extraLarge)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "CONECTADO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Painel Flutuante",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Desenvolvido por: $_creator",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            Button(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        val serviceIntent = Intent(context, FloatingPanelService::class.java)
                        context.startService(serviceIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Abrir Menu Flutuante", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
        }
    }
}
