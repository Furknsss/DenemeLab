package com.denemelab.preview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private val LoginInk = Color(0xFF191D25)
private val LoginMuted = Color(0xFF758090)
private val LoginAccent = Color(0xFF077F85)
private val LoginSurface = Color(0xFFF9FAFC)

private fun Context.asActivity(): Activity? {
    var ctx: Context = this
    while (true) {
        if (ctx is Activity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext ?: return null
    }
}

/** Sign-in is the launch destination, NOT an optional settings pane. */
@Composable
fun AccountAwareApp() {
    val context = LocalContext.current
    val enabled = remember(context) { FirebaseCloudBackup.configured(context) }
    var uid by remember(context) { mutableStateOf(FirebaseCloudBackup.accountUid(context)) }
    var state by remember(uid) { mutableStateOf<WorkspaceResult?>(null) }
    var failure by remember(uid) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context, enabled) {
        if (enabled) {
            val firebase = FirebaseAuth.getInstance()
            val listener = FirebaseAuth.AuthStateListener {
                uid = it.currentUser?.uid
            }
            firebase.addAuthStateListener(listener)
            onDispose { firebase.removeAuthStateListener(listener) }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(uid) {
        val active = uid
        if (active == null) {
            state = null
            failure = null
        } else {
            failure = null
            state = null
            try {
                state = AccountWorkspace.prepare(context, active)
            } catch (error: Exception) {
                failure = error.localizedMessage ?: "Hesap verileri yüklenemedi."
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = LoginInk, onPrimary = Color.White,
        background = LoginSurface, onBackground = LoginInk,
        surface = Color.White, onSurface = LoginInk
    )) {
        when {
            !enabled -> AccountStatusPage(
                title = "Bağlantı yapılandırması eksik",
                message = "Firebase ayarları bu APK'ya eklenmemiş. Hesap ve bulut yedekleme olmadan oturum açılamaz."
            )
            uid == null -> DenemeLabSignIn()
            state?.ready == true -> key(uid) {
                DenemeLabApp()
            }
            else -> {
                val id = uid
                AccountStatusPage(
                    title = if (state?.needsChoice == true) "Veri kaynağını seç"
                        else "Hesabın hazırlanıyor",
                    message = failure ?: state?.message ?: "Kayıtların korunuyor.",
                    busy = state == null && failure == null,
                    choices = state?.needsChoice == true,
                    onCloud = {
                        if (id != null) {
                            failure = null; state = null
                            scope.launch {
                                try { state = AccountWorkspace.prepare(context, id, true) }
                                catch (e: Exception) {
                                    failure = e.localizedMessage ?: "Bulut yedeği yüklenemedi."
                                }
                            }
                        }
                    },
                    onLocal = {
                        if (id != null) {
                            failure = null; state = null
                            scope.launch {
                                try { state = AccountWorkspace.prepare(context, id, false) }
                                catch (e: Exception) {
                                    failure = e.localizedMessage ?: "Cihaz verileri açılamadı."
                                }
                            }
                        }
                    },
                    onRetry = {
                        if (id != null) {
                            failure = null; state = null
                            scope.launch {
                                try { state = AccountWorkspace.prepare(context, id) }
                                catch (e: Exception) {
                                    failure = e.localizedMessage ?: "Hesap açılamadı."
                                }
                            }
                        }
                    },
                    onSignOut = { FirebaseCloudBackup.signOut(context) }
                )
            }
        }
    }
}

@Composable
private fun AccountStatusPage(
    title: String,
    message: String,
    busy: Boolean = false,
    choices: Boolean = false,
    onCloud: () -> Unit = {},
    onLocal: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(LoginSurface)
        .statusBarsPadding().navigationBarsPadding().padding(25.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_denemelab), null,
                modifier = Modifier.size(62.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LoginInk,
                textAlign = TextAlign.Center)
            Text(message, fontSize = 13.sp, color = LoginMuted,
                textAlign = TextAlign.Center)
            if (busy) CircularProgressIndicator(color = LoginAccent)
            if (choices) {
                Button(onClick = onCloud, modifier = Modifier.fillMaxWidth()) {
                    Text("Buluttaki hesabımı aç")
                }
                OutlinedButton(onClick = onLocal, modifier = Modifier.fillMaxWidth()) {
                    Text("Bu cihazdaki kayıtlarımı koru")
                }
                Text("Hiçbir veri onay vermeden silinmez. İki ayrı veri kümesi otomatik birleştirilmez.",
                    fontSize = 11.sp, color = LoginMuted, textAlign = TextAlign.Center)
            }
            if (!busy && !choices) {
                OutlinedButton(onClick = onRetry) { Text("Tekrar dene") }
            }
            if (!busy) TextButton(onClick = onSignOut) { Text("Başka hesapla giriş yap") }
        }
    }
}

@Composable
private fun DenemeLabSignIn() {
    val context = LocalContext.current
    val activity = remember(context) { context.asActivity() }
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var compatibility by remember {
        mutableStateOf(GoogleAccountAuth.preferredFlow(Build.VERSION.SDK_INT) ==
            GoogleAccountAuth.Flow.COMPATIBILITY)
    }
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                GoogleAccountAuth.signInWithCompatibilityResult(
                    context, result.resultCode, result.data)
            } catch (exception: Exception) {
                error = exception.localizedMessage ?: "Google girişi tamamlanamadı."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(LoginSurface)
            .statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.ic_denemelab),
            contentDescription = "DenemeLab", modifier = Modifier.size(67.dp))
        Spacer(Modifier.height(13.dp))
        Text("DenemeLab", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = LoginInk)
        Spacer(Modifier.height(4.dp))
        Text("Tüm denemelerin. Tek hesabın.", color = LoginMuted, fontSize = 13.sp)
        Spacer(Modifier.height(31.dp))
        Surface(shape = RoundedCornerShape(25.dp), color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE5EAF0)),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(if (registering) "Hesap oluştur" else "Hoş geldin",
                    fontSize = 23.sp, fontWeight = FontWeight.Bold, color = LoginInk)
                Spacer(Modifier.height(8.dp))
                Text("Denemelerin, yayın logoların, hedeflerin ve profilin hesabında korunsun.",
                    color = LoginMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(19.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    AccountChoice("Giriş yap", !registering, Modifier.weight(1f)) {
                        registering = false; error = null; message = null
                    }
                    AccountChoice("Kayıt ol", registering, Modifier.weight(1f)) {
                        registering = true; error = null; message = null
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim(); error = null },
                    label = { Text("E-posta adresi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    enabled = !busy
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Şifre") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp), enabled = !busy
                )
                if (registering) {
                    Spacer(Modifier.height(5.dp))
                    Text("En az 8 karakter", fontSize = 11.sp, color = LoginMuted)
                } else {
                    TextButton(onClick = {
                        if (email.isBlank()) {
                            error = "Önce e-posta adresini gir."
                        } else {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    FirebaseCloudBackup.sendPasswordReset(context, email)
                                    message = "Şifre sıfırlama bağlantısı gönderildi."
                                } catch (e: Exception) {
                                    error = e.localizedMessage ?: "Bağlantı gönderilemedi."
                                } finally { busy = false }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                        Text("Şifremi unuttum", fontSize = 12.sp, color = LoginAccent)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = {
                        busy = true; error = null; message = null
                        scope.launch {
                            try {
                                if (registering) FirebaseCloudBackup.registerWithEmail(
                                    context, email, password)
                                else FirebaseCloudBackup.signInWithEmail(
                                    context, email, password)
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: "Giriş yapılamadı."
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email).matches() &&
                        password.length >= (if (registering) 8 else 1),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LoginInk)
                ) { Text(if (registering) "Hesabımı oluştur" else "Giriş yap") }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE5EAF0))
                    Text("  veya  ", color = LoginMuted, fontSize = 11.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE5EAF0))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFD7DDE5)),
                    onClick = {
                        error = null; message = null
                        if (activity == null) {
                            error = "Google girişi bu cihazda başlatılamadı."
                        } else {
                            busy = true
                            if (compatibility) {
                                try {
                                    googleLauncher.launch(GoogleAccountAuth.compatibilityIntent(activity))
                                } catch (e: Exception) {
                                    busy = false
                                    error = e.localizedMessage ?: "Google seçimi açılamadı."
                                }
                            } else scope.launch {
                                try {
                                    GoogleAccountAuth.signInWithCredentialManager(activity)
                                } catch (e: Exception) {
                                    compatibility = true
                                    error = e.localizedMessage ?: "Google girişi başarısız."
                                } finally { busy = false }
                            }
                        }
                    }
                ) {
                    Text("G", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        color = LoginAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Google ile devam et", color = LoginInk,
                        fontWeight = FontWeight.SemiBold)
                }
                if (compatibility && Build.VERSION.SDK_INT < 36) {
                    Spacer(Modifier.height(5.dp))
                    Text("Alternatif Google hesap seçici", color = LoginMuted, fontSize = 10.sp)
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = LoginAccent, fontSize = 12.sp)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFB34C5E), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Text("Hesabınla giriş yaptıktan sonra verilerin bulut yedeğinden alınır.",
            color = LoginMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AccountChoice(title: String, selected: Boolean, modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) LoginInk else LoginSurface) {
        Text(title, color = if (selected) Color.White else LoginMuted,
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(13.dp))
    }
}
