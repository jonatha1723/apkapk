package com.example

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var fovComposeView: ComposeView? = null
    private var panelLifecycleOwner: OverlayLifecycleOwner? = null
    private var fovLifecycleOwner: OverlayLifecycleOwner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        panelLifecycleOwner = OverlayLifecycleOwner()
        panelLifecycleOwner?.onCreate()
        showFloatingPanel()
    }

    private fun showFloatingPanel() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(panelLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(panelLifecycleOwner)
            setViewTreeViewModelStoreOwner(panelLifecycleOwner)

            setContent {
                MaterialTheme {
                    FloatingUI(
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this@apply, params)
                        },
                        onFovToggle = { isVisible -> toggleFov(isVisible, windowType) }
                    )
                }
            }
        }

        windowManager.addView(composeView, params)
        panelLifecycleOwner?.onResume()
    }

    private fun toggleFov(visible: Boolean, windowType: Int) {
        if (visible) {
            if (fovComposeView == null) {
                fovLifecycleOwner = OverlayLifecycleOwner()
                fovLifecycleOwner?.onCreate()

                val fovParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                fovComposeView = ComposeView(this).apply {
                    setViewTreeLifecycleOwner(fovLifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(fovLifecycleOwner)
                    setViewTreeViewModelStoreOwner(fovLifecycleOwner)
                    setContent {
                        FovOverlay()
                    }
                }
                windowManager.addView(fovComposeView, fovParams)
                fovLifecycleOwner?.onResume()
            }
        } else {
            fovComposeView?.let {
                windowManager.removeView(it)
                fovComposeView = null
                fovLifecycleOwner?.onDestroy()
                fovLifecycleOwner = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let { windowManager.removeView(it) }
        panelLifecycleOwner?.onDestroy()
        
        fovComposeView?.let { windowManager.removeView(it) }
        fovLifecycleOwner?.onDestroy()
    }
}

@Composable
fun FovOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val fovRadius = 250f
        drawCircle(
            color = Color(0xFFE50000), // Red FOV circle
            radius = fovRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // Crosshair lines
        val lineLength = 20f
        drawLine(
            color = Color(0xFFE50000),
            start = Offset(center.x, center.y - lineLength),
            end = Offset(center.x, center.y + lineLength),
            strokeWidth = 3f
        )
        drawLine(
            color = Color(0xFFE50000),
            start = Offset(center.x - lineLength, center.y),
            end = Offset(center.x + lineLength, center.y),
            strokeWidth = 3f
        )
    }
}

@Composable
fun FloatingUI(onClose: () -> Unit, onDrag: (Float, Float) -> Unit, onFovToggle: (Boolean) -> Unit) {
    var isMinimized by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("GERAL", "VISUAL", "OUTROS")

    val accentColor = Color.White
    val bgColor = Color(0xFF101010)
    val headerColor = Color(0xFF080808)

    var timeStr by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val expiryTime = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Sao_Paulo")).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 13)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        while(true) {
            val now = System.currentTimeMillis()
            val diff = expiryTime - now
            if (diff <= 0) {
                onClose()
            } else {
                val h = (diff / (1000 * 60 * 60)) % 24
                val m = (diff / (1000 * 60)) % 60
                val s = (diff / 1000) % 60
                timeStr = String.format("Expira: %02d:%02d:%02d", h, m, s)
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF252525), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Column {
            // Header (Draggable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MENU DE AJUSTES",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = timeStr,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimizar",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isMinimized = !isMinimized }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar Painel",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onClose() }
                    )
                }
            }

            if (!isMinimized) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF151515),
                    contentColor = accentColor,
                    divider = { HorizontalDivider(color = Color(0xFF252525)) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == index) accentColor else Color.Gray
                                )
                            }
                        )
                    }
                }

                // Toggles
                Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)) {
                    when (selectedTab) {
                        0 -> { // Aim
                            PanelSwitch("Otimização de Toque", "Melhora a precisão na tela")
                            PanelSwitch("Indicador Central", "Abre um círculo na tela central", onToggle = onFovToggle)
                            PanelSwitch("Filtro de Tela", "Suaviza cores")
                            PanelSwitch("Modo Desempenho", "Reduz o lag")
                        }
                        1 -> { // Visuals
                            PanelSwitch("Sobreposição de Contorno", "Destaque visual de objetos")
                            PanelSwitch("Modo Leitura", "Filtro anti luz-azul")
                            PanelSwitch("Indicador de Status", "Marcador no topo da tela")
                            PanelSwitch("Ajuste de Brilho", "Aumenta detalhes escuros")
                        }
                        2 -> { // Functions
                            PanelSwitch("Economia de Bateria", "Reduz atividades de fundo")
                            PanelSwitch("Animações Rápidas", "Acelera as transições")
                            PanelSwitch("Modo Discreto", "Esconde notificações sensíveis")
                            PanelSwitch("Filtro Anti-Spam", "Segurança adicional ativada")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PanelSwitch(title: String, subtitle: String = "", onToggle: ((Boolean) -> Unit)? = null) {
    var checked by remember { mutableStateOf(false) }
    
    val accentColor = Color.White
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                color = Color.White, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle, 
                    color = Color.Gray, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                onToggle?.invoke(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color(0xFF999999),
                uncheckedTrackColor = Color(0xFF2C2C2C),
                uncheckedBorderColor = Color.Transparent
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private var mLifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private var mSavedStateRegistryController: SavedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    fun onCreate() {
        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
