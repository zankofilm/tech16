package ir.madreseyar.teacher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private val Purple900 = Color(0xFF2B0A67)
private val Purple800 = Color(0xFF3A0D88)
private val Purple700 = Color(0xFF5B20D6)
private val Purple500 = Color(0xFF7C3AED)
private val AppBackground = Color(0xFFF6F7FC)
private val TextPrimary = Color(0xFF151B2D)
private val TextSecondary = Color(0xFF6B7280)
private val Border = Color(0xFFE5E7EB)
private val Success = Color(0xFF0F8A5F)

class MainActivity : FragmentActivity() {
    private lateinit var tokenStore: SecureTokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        tokenStore = SecureTokenStore(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { TeacherDirectApiApp(this, tokenStore) }
            }
        }
    }

    fun biometricAvailable(): Boolean {
        val result = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن به مدرسه‌یار")
            .setSubtitle("اثر انگشت یا قفل دستگاه را تأیید کنید")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

private enum class Screen { Login, ChangePassword, Home, Classes, Messages, More, Attendance, Grades, Exams, Schedule, Notifications, Practice, Library, Account }

private data class MenuFeature(val title: String, val icon: ImageVector, val screen: Screen, val subtitle: String)

@Composable
private fun TeacherDirectApiApp(activity: MainActivity, tokenStore: SecureTokenStore) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Login) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mfaRequired by remember { mutableStateOf(false) }
    var recoveryAllowed by remember { mutableStateOf(false) }
    var pendingUser by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var workspace by remember { mutableStateOf<JSONObject?>(null) }
    var currentPasswordForChange by remember { mutableStateOf("") }

    suspend fun loadCore() {
        dashboard = ApiClient.get("/api/teacher/dashboard")
        workspace = ApiClient.get("/api/teacher/workspace")
    }

    fun fail(e: Throwable) {
        error = e.message ?: "خطای نامشخص"
        if (e is ApiException && e.status in listOf(401, 403)) {
            ApiClient.token = ""
            tokenStore.clear()
            screen = Screen.Login
        }
    }

    fun finishLogin(data: JSONObject, enteredPassword: String) {
        val user = data.optJSONObject("user")
        if (user?.optString("role") != "teacher") {
            error = "این اپ فقط برای حساب معلم قابل استفاده است."
            return
        }
        val token = data.optString("token")
        if (token.isBlank()) {
            error = "سرور توکن ورود برنگرداند."
            return
        }
        ApiClient.token = token
        if (activity.biometricAvailable()) tokenStore.save(token)
        currentPasswordForChange = enteredPassword
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        loginPassword = ""
        scope.launch {
            busy = true; error = ""
            try {
                if (data.optBoolean("mustChangePassword", false)) screen = Screen.ChangePassword
                else { loadCore(); screen = Screen.Home }
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun login(identifier: String, password: String) {
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 428 || e.code == "MFA_REQUIRED" || e.payload?.optBoolean("mfaRequired") == true) {
                    mfaRequired = true
                    recoveryAllowed = e.payload?.optBoolean("recoveryAllowed", false) == true
                    pendingUser = identifier
                    pendingPassword = password
                    error = ""
                } else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun verifyMfa(code: String, useRecovery: Boolean) {
        val identifier = pendingUser
        val password = pendingPassword
        if (identifier.isBlank() || password.isBlank()) {
            mfaRequired = false
            recoveryAllowed = false
            error = "نشست ورود منقضی شده است؛ دوباره وارد شوید."
            return
        }
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                if (useRecovery) body.put("recoveryCode", code.trim()) else body.put("otp", code.filter(Char::isDigit))
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 401 || e.code == "MFA_INVALID") error = if (useRecovery) "کد بازیابی صحیح نیست." else "کد تأیید صحیح نیست."
                else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun cancelMfa() {
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        error = "ورود دومرحله‌ای لغو شد."
    }

    fun biometricLogin() {
        activity.authenticateBiometric(onSuccess = {
            scope.launch {
                busy = true; error = ""
                try {
                    val token = tokenStore.load() ?: throw IllegalStateException("نشست ذخیره‌شده پیدا نشد.")
                    ApiClient.token = token
                    val me = ApiClient.get("/api/staff/me")
                    if (me.optJSONObject("user")?.optString("role") != "teacher") throw IllegalStateException("نشست معلم معتبر نیست.")
                    loadCore(); screen = Screen.Home
                } catch (e: Throwable) { tokenStore.clear(); fail(e) } finally { busy = false }
            }
        }, onError = { error = it })
    }

    fun refreshCore() {
        scope.launch { busy = true; error = ""; try { loadCore() } catch (e: Throwable) { fail(e) } finally { busy = false } }
    }

    fun logout() {
        scope.launch {
            try { if (ApiClient.token.isNotBlank()) ApiClient.post("/api/staff/logout") } catch (_: Throwable) {}
            ApiClient.token = ""; tokenStore.clear(); dashboard = null; workspace = null; screen = Screen.Login
        }
    }

    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            Screen.Login -> {
                LoginScreen(
                    username = loginIdentifier,
                    password = loginPassword,
                    onUsernameChange = { loginIdentifier = it },
                    onPasswordChange = { loginPassword = it },
                    hasBiometric = tokenStore.hasToken() && activity.biometricAvailable(),
                    busy = busy,
                    error = if (mfaRequired) "" else error,
                    onLogin = { u, p -> login(u, p) },
                    onBiometric = { biometricLogin() }
                )
                if (mfaRequired) {
                    MfaDialog(
                        busy = busy,
                        error = error,
                        recoveryAllowed = recoveryAllowed,
                        onVerify = { code, useRecovery -> verifyMfa(code, useRecovery) },
                        onCancel = { cancelMfa() }
                    )
                }
            }
            Screen.ChangePassword -> ChangePasswordScreen(busy, error) { newPassword ->
                scope.launch {
                    busy = true; error = ""
                    try {
                        ApiClient.post("/api/staff/change-password", JSONObject().put("currentPassword", currentPasswordForChange).put("newPassword", newPassword))
                        loadCore(); screen = Screen.Home
                    } catch (e: Throwable) { fail(e) } finally { busy = false }
                }
            }
            else -> NativeShell(
                screen = target,
                dashboard = dashboard,
                workspace = workspace,
                busy = busy,
                error = error,
                onNavigate = { screen = it },
                onRefresh = { refreshCore() },
                onLogout = { logout() }
            )
        }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    hasBiometric: Boolean,
    busy: Boolean,
    error: String,
    onLogin: (String, String) -> Unit,
    onBiometric: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
        contentPadding = PaddingValues(24.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Surface(Modifier.size(112.dp), RoundedCornerShape(32.dp), Color.White, shadowElevation = 8.dp) {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.padding(14.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("مدرسه‌یار", color = Purple900, fontWeight = FontWeight.Black, fontSize = 34.sp)
            Text("پنل معلم", color = TextSecondary, fontSize = 17.sp)
            Spacer(Modifier.height(28.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(3.dp)) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(username, onUsernameChange, Modifier.fillMaxWidth(), label = { Text("نام کاربری") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(password, onPasswordChange, Modifier.fillMaxWidth(), label = { Text("رمز عبور") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
                    if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password) },
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple700)
                    ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text("ورود به حساب", fontWeight = FontWeight.Bold) }
                    if (hasBiometric) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onBiometric, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("ورود با اثر انگشت")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MfaDialog(
    busy: Boolean,
    error: String,
    recoveryAllowed: Boolean,
    onVerify: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var useRecovery by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        icon = { Icon(Icons.Default.Security, null, tint = Purple700) },
        title = { Text("تأیید دومرحله‌ای", fontWeight = FontWeight.Black, color = Purple900) },
        text = {
            Column {
                Text(
                    if (useRecovery) "کد بازیابی را وارد کنید." else "کد ۶ رقمی برنامه Authenticator را وارد کنید.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                if (!useRecovery) {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد Authenticator") },
                        placeholder = { Text("000000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = recoveryCode,
                        onValueChange = { recoveryCode = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد بازیابی") },
                        placeholder = { Text("ABCD-EFGH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (recoveryAllowed) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { useRecovery = !useRecovery },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (useRecovery) "استفاده از کد Authenticator" else "استفاده از کد بازیابی")
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("انصراف") }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(if (useRecovery) recoveryCode.trim() else otp.trim(), useRecovery) },
                enabled = !busy && (if (useRecovery) recoveryCode.isNotBlank() else otp.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = Purple700)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("تأیید و ورود")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun ChangePasswordScreen(busy: Boolean, error: String, onSubmit: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تغییر رمز اجباری", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Purple900)
        Spacer(Modifier.height(8.dp)); Text("سرور درخواست کرده قبل از ادامه رمز عبور را تغییر دهید.", color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(p1, { p1 = it }, Modifier.fillMaxWidth(), label = { Text("رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(p2, { p2 = it }, Modifier.fillMaxWidth(), label = { Text("تکرار رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { if (p1.length >= 8 && p1 == p2) onSubmit(p1) }, enabled = !busy && p1.length >= 8 && p1 == p2, modifier = Modifier.fillMaxWidth()) { Text("ثبت رمز و ادامه") }
    }
}

@Composable
private fun NativeShell(
    screen: Screen,
    dashboard: JSONObject?,
    workspace: JSONObject?,
    busy: Boolean,
    error: String,
    onNavigate: (Screen) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val rootTab = when (screen) {
        Screen.Home, Screen.Attendance, Screen.Notifications -> Screen.Home
        Screen.Classes, Screen.Grades, Screen.Exams, Screen.Schedule -> Screen.Classes
        Screen.Messages -> Screen.Messages
        else -> Screen.More
    }
    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    Triple(Screen.Home, "خانه", Icons.Outlined.Home),
                    Triple(Screen.Classes, "کلاس‌ها", Icons.Outlined.Groups),
                    Triple(Screen.Messages, "پیام‌ها", Icons.Outlined.ChatBubbleOutline),
                    Triple(Screen.More, "بیشتر", Icons.Outlined.MoreHoriz),
                ).forEach { (s, label, icon) ->
                    NavigationBarItem(selected = rootTab == s, onClick = { onNavigate(s) }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Home -> HomeScreen(dashboard, workspace, onNavigate, onRefresh)
                Screen.Classes -> ClassesScreen(workspace, onNavigate)
                Screen.Messages -> MessagesScreen()
                Screen.More -> MoreScreen(onNavigate, onLogout)
                Screen.Attendance -> AttendanceScreen(dashboard, onRefresh)
                Screen.Grades -> GradesScreen(workspace)
                Screen.Exams -> ExamsScreen(workspace)
                Screen.Schedule -> ScheduleScreen(dashboard)
                Screen.Notifications -> NotificationsScreen(dashboard)
                Screen.Practice -> SimpleApiListScreen("تمرین‌ها", "/api/practice/p59/list", "quizzes")
                Screen.Library -> SimpleApiListScreen("کتابخانه دیجیتال", "/api/library/p62/list", "items")
                Screen.Account -> AccountScreen(onLogout)
                else -> Unit
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (error.isNotBlank() && screen != Screen.Login) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = Color(0xFFFFEEEE), shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
                    Text(error, Modifier.padding(14.dp), color = Color(0xFFA52929), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(dashboard: JSONObject?, workspace: JSONObject?, onNavigate: (Screen) -> Unit, onRefresh: () -> Unit) {
    val teacher = dashboard?.optJSONObject("teacher")
    val school = dashboard?.optJSONObject("school")
    val clock = dashboard?.optJSONObject("clock")
    val current = dashboard?.optJSONObject("currentClass")
    val notifications = dashboard?.optJSONArray("notifications") ?: JSONArray()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Purple900, Purple700))).statusBarsPadding().padding(20.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("سلام ${teacher?.optString("firstName").orEmpty()} ${teacher?.optString("lastName").orEmpty()}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(school?.optString("name", "مدرسه‌یار") ?: "مدرسه‌یار", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        }
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(18.dp)) {
                        Text("${clock?.optString("dayName").orEmpty()} ${clock?.optString("dateFa").orEmpty()}  •  ${clock?.optString("time", "--:--")}", Modifier.padding(14.dp), color = Color.White)
                    }
                }
            }
        }
        if (current != null) item {
            SectionCard("کلاس جاری", Icons.Default.Class) {
                Text("${current.optString("subject")} · ${current.optString("className")}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp)); Text("${current.optString("startTime")} تا ${current.optString("endTime")} · زنگ ${current.optInt("periodNo")}", color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onNavigate(Screen.Attendance) }, enabled = current.optBoolean("attendanceOpen", false), colors = ButtonDefaults.buttonColors(containerColor = Success)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("ثبت حضور و غیاب") }
            }
        }
        item {
            Text("دسترسی سریع", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), fontWeight = FontWeight.Black, fontSize = 19.sp)
            val features = listOf(
                MenuFeature("کلاس‌های من", Icons.Default.Groups, Screen.Classes, "دانش‌آموزان و درس‌ها"),
                MenuFeature("نمرات", Icons.Default.AutoGraph, Screen.Grades, "دفتر نمره مستقیم"),
                MenuFeature("آزمون‌ها", Icons.Default.Assignment, Screen.Exams, "آزمون‌های من"),
                MenuFeature("پیام‌ها", Icons.Default.ChatBubble, Screen.Messages, "ارتباط درسی"),
                MenuFeature("برنامه هفتگی", Icons.Default.CalendarMonth, Screen.Schedule, "برنامه واقعی سرور"),
                MenuFeature("اعلان‌ها", Icons.Default.Notifications, Screen.Notifications, "${notifications.length()} اعلان"),
            )
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 12.dp), userScrollEnabled = false) {
                gridItems(features) { f -> QuickFeature(f) { onNavigate(f.screen) } }
            }
        }
        val today = dashboard?.optJSONArray("todaySchedule") ?: JSONArray()
        item { Text("برنامه امروز", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Black, fontSize = 19.sp) }
        items(today.toObjects()) { row ->
            SimpleCard {
                Text("${row.optString("subject")} · ${row.optString("className")}", fontWeight = FontWeight.Bold)
                Text("${row.optString("startTime")}–${row.optString("endTime")} · زنگ ${row.optInt("periodNo")}", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun QuickFeature(feature: MenuFeature, onClick: () -> Unit) {
    Card(Modifier.padding(6.dp).fillMaxWidth().height(112.dp).clickable(onClick = onClick), RoundedCornerShape(20.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(Modifier.size(38.dp), CircleShape, Purple700.copy(alpha = .1f)) { Box(contentAlignment = Alignment.Center) { Icon(feature.icon, null, tint = Purple700) } }
            Spacer(Modifier.height(6.dp)); Text(feature.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text(feature.subtitle, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ClassesScreen(workspace: JSONObject?, onNavigate: (Screen) -> Unit) {
    val assignments = workspace?.optJSONArray("assignments") ?: JSONArray()
    val classes = workspace?.optJSONArray("classes") ?: JSONArray()
    val students = workspace?.optJSONArray("students") ?: JSONArray()
    val classMap = classes.toObjects().associateBy { it.optString("id") }
    val unique = assignments.toObjects().distinctBy { "${it.optString("classId")}|${it.optString("subject")}" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("کلاس‌های من")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(unique) { a ->
                val cid = a.optString("classId"); val count = students.toObjects().count { it.optString("classId") == cid }
                SimpleCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(48.dp), CircleShape, Purple700.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = Purple700) } }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${a.optString("subject")} · ${classMap[cid]?.optString("name", cid) ?: cid}", fontWeight = FontWeight.Black)
                            Text("$count دانش‌آموز", color = TextSecondary, fontSize = 13.sp)
                            val names = students.toObjects().filter { it.optString("classId") == cid }.take(5).joinToString("، ") { "${it.optString("firstName")} ${it.optString("lastName")}" }
                            if (names.isNotBlank()) Text(names, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onNavigate(Screen.Grades) }, Modifier.weight(1f)) { Text("دفتر نمره") }
                OutlinedButton(onClick = { onNavigate(Screen.Schedule) }, Modifier.weight(1f)) { Text("برنامه هفتگی") }
            } }
        }
    }
}

@Composable
private fun AttendanceScreen(dashboard: JSONObject?, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope(); val current = dashboard?.optJSONObject("currentClass")
    var saving by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    if (current == null) { Column { ScreenHeader("حضور و غیاب"); EmptyState("در حال حاضر کلاس فعالی وجود ندارد.") }; return }
    val students = current.optJSONArray("students")?.toObjects().orEmpty()
    val savedRecords = current.optJSONObject("attendance")?.optJSONArray("records")?.toObjects().orEmpty().associate { it.optString("studentId") to it.optString("status") }
    val present = remember(current.toString()) { mutableStateMapOf<String, Boolean>().apply { students.forEach { s -> put(s.optString("id"), if (savedRecords.isEmpty()) true else savedRecords[s.optString("id")] == "present") } } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("حضور و غیاب")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { SimpleCard { Text("${current.optString("subject")} · ${current.optString("className")}", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("${current.optString("startTime")} تا ${current.optString("endTime")}", color = TextSecondary) } }
            items(students) { s ->
                val id = s.optString("id")
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = present[id] == true, onCheckedChange = { present[id] = it })
                        Column { Text("${s.optString("firstName")} ${s.optString("lastName")}", fontWeight = FontWeight.Bold); Text(s.optString("studentCode"), color = TextSecondary, fontSize = 12.sp) }
                    }
                }
            }
            item {
                if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp), color = if (message.startsWith("ثبت شد")) Success else MaterialTheme.colorScheme.error)
                Button(onClick = {
                    scope.launch { saving = true; message = ""; try {
                        val ids = JSONArray(); students.forEach { s -> if (present[s.optString("id")] == true) ids.put(s.optString("id")) }
                        val result = ApiClient.post("/api/teacher/attendance/submit", JSONObject().put("scheduleEntryId", current.optString("id")).put("presentStudentIds", ids))
                        val summary = result.optJSONObject("summary"); message = "ثبت شد: ${summary?.optInt("present", ids.length())} حاضر"
                        onRefresh()
                    } catch (e: Throwable) { message = e.message ?: "خطا" } finally { saving = false } }
                }, enabled = !saving && current.optBoolean("attendanceOpen", false), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (saving) "در حال ثبت…" else "ثبت حضور و غیاب") }
            }
        }
    }
}

@Composable
private fun GradesScreen(workspace: JSONObject?) {
    val scope = rememberCoroutineScope(); val assignments = workspace?.optJSONArray("assignments")?.toObjects().orEmpty().distinctBy { "${it.optString("classId")}|${it.optString("subject")}" }
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }; var period by remember { mutableStateOf("semester2") }; var gradebook by remember { mutableStateOf<JSONObject?>(null) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    fun load(a: JSONObject) { selected = a; scope.launch { loading = true; error = ""; try { val c = URLEncoder.encode(a.optString("classId"), "UTF-8"); val s = URLEncoder.encode(a.optString("subject"), "UTF-8"); gradebook = ApiClient.get("/api/staff/gradebook?classId=$c&subject=$s&academicPeriod=$period") } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("دفتر نمره")
        if (selected == null) LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { Text("کلاس و درس را انتخاب کنید", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(assignments) { a -> SimpleCard(Modifier.clickable { load(a) }) { Text("${a.optString("subject")} · ${classes[a.optString("classId")]?.optString("name", a.optString("classId"))}", fontWeight = FontWeight.Black); Text("ورود به دفتر نمره", color = Purple700, fontSize = 12.sp) } }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { period = "semester1"; selected?.let { load(it) } }, label = { Text("نیم‌سال اول") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester1") Purple700.copy(.12f) else Color.White))
                AssistChip(onClick = { period = "semester2"; selected?.let { load(it) } }, label = { Text("نیم‌سال دوم") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester2") Purple700.copy(.12f) else Color.White))
                TextButton(onClick = { selected = null; gradebook = null }) { Text("تغییر کلاس") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            gradebook?.let { GradebookEditor(it, selected!!, period) }
        }
    }
}

@Composable
private fun GradebookEditor(book: JSONObject, assignment: JSONObject, period: String) {
    val scope = rememberCoroutineScope(); val categories = book.optJSONObject("config")?.optJSONArray("categories")?.toObjects().orEmpty().filter { it.optString("source") != "exam" }
    val students = book.optJSONArray("students")?.toObjects().orEmpty(); val category = categories.firstOrNull()
    val scores = remember(book.toString()) { mutableStateMapOf<String, String>() }; val notes = remember(book.toString()) { mutableStateMapOf<String, String>() }; var message by remember { mutableStateOf("") }
    if (category == null) { EmptyState("برای این درس مؤلفه دستی دفتر نمره تعریف نشده است."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(students) { st ->
            val id = st.optString("id"); val current = st.optJSONObject("result")?.opt("score")?.toString()?.takeIf { it != "null" }.orEmpty()
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(18.dp), CardDefaults.cardColors(Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${st.optString("firstName")} ${st.optString("lastName")}", fontWeight = FontWeight.Black)
                    Text("نمره مستمر محاسبه‌شده: ${current.ifBlank { "—" }}", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(scores[id].orEmpty(), { scores[id] = it }, Modifier.weight(.34f), label = { Text("از ۲۰") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(notes[id].orEmpty(), { notes[id] = it }, Modifier.weight(.66f), label = { Text("توضیح") }, singleLine = true)
                    }
                    TextButton(onClick = {
                        val score = scores[id]?.toDoubleOrNull(); if (score == null || score !in 0.0..20.0) { message = "نمره ${st.optString("firstName")} معتبر نیست."; return@TextButton }
                        scope.launch { try { ApiClient.post("/api/staff/gradebook/entry", JSONObject().put("studentId", id).put("classId", assignment.optString("classId")).put("subject", assignment.optString("subject")).put("categoryId", category.optString("id")).put("academicPeriod", period).put("score", score).put("maxScore", 20).put("note", notes[id].orEmpty())); message = "نمره ${st.optString("firstName")} ثبت شد ✓" } catch (e: Throwable) { message = e.message ?: "خطا" } }
                    }) { Text("ثبت نمره") }
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, Modifier.padding(8.dp), color = if (message.contains("✓")) Success else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExamsScreen(workspace: JSONObject?) {
    val exams = workspace?.optJSONArray("exams")?.toObjects().orEmpty(); val attempts = workspace?.optJSONArray("attempts")?.toObjects().orEmpty(); val submissions = workspace?.optJSONArray("submissions")?.toObjects().orEmpty(); val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    Column(Modifier.fillMaxSize()) { ScreenHeader("آزمون‌های من"); LazyColumn(contentPadding = PaddingValues(16.dp)) { items(exams) { e ->
        val done = attempts.count { it.optString("examId") == e.optString("id") } + submissions.count { it.optString("examId") == e.optString("id") }
        SimpleCard { Text(e.optString("title", "آزمون"), fontWeight = FontWeight.Black); Text("${e.optString("subject")} · ${classes[e.optString("classId")]?.optString("name", e.optString("classId"))}", color = TextSecondary); Text("وضعیت: ${e.optString("publicationState", e.optString("status"))} · تحویل: $done · بارم: ${e.optDouble("totalScore", 0.0)}", fontSize = 12.sp, color = TextSecondary) }
    }; if (exams.isEmpty()) item { EmptyState("آزمونی در محدوده تدریس شما نیست.") } } }
}

@Composable
private fun ScheduleScreen(dashboard: JSONObject?) {
    val rows = dashboard?.optJSONArray("weekSchedule")?.toObjects().orEmpty(); val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    Column(Modifier.fillMaxSize()) { ScreenHeader("برنامه هفتگی"); LazyColumn(contentPadding = PaddingValues(16.dp)) { days.forEach { day -> val ds = rows.filter { it.optString("day") == day }; if (ds.isNotEmpty()) item { SimpleCard { Text(day, fontWeight = FontWeight.Black, color = Purple800); ds.forEach { x -> Text("${x.optString("startTime")}–${x.optString("endTime")}  ${x.optString("subject")} · ${x.optString("className")}", Modifier.padding(top = 6.dp)) } } } } } }
}

@Composable
private fun NotificationsScreen(dashboard: JSONObject?) {
    val rows = dashboard?.optJSONArray("notifications")?.toObjects().orEmpty()
    Column(Modifier.fillMaxSize()) { ScreenHeader("اعلان‌ها"); LazyColumn(contentPadding = PaddingValues(16.dp)) { items(rows) { n -> SimpleCard { Text(n.optString("title", "اعلان"), fontWeight = FontWeight.Black); Text(n.optString("body"), color = TextSecondary); Text(n.optString("createdAt"), color = TextSecondary, fontSize = 10.sp) } }; if (rows.isEmpty()) item { EmptyState("اعلان جدیدی ندارید.") } } }
}

@Composable
private fun MessagesScreen() {
    val scope = rememberCoroutineScope(); var contacts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var selected by remember { mutableStateOf<JSONObject?>(null) }; var draft by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(true) }
    suspend fun reload() { val c = ApiClient.get("/api/academic-messages/p58/contacts"); val t = ApiClient.get("/api/academic-messages/p58/threads"); contacts = c.optJSONArray("contacts")?.toObjects().orEmpty(); messages = t.optJSONArray("messages")?.toObjects().orEmpty() }
    LaunchedEffect(Unit) { try { reload() } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (selected == null) "پیام‌ها" else selected!!.optString("name", "گفتگو"))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (selected == null) LazyColumn(contentPadding = PaddingValues(16.dp)) { items(contacts.distinctBy { "${it.optString("studentId")}|${it.optString("subject")}" }) { c ->
            SimpleCard(Modifier.clickable { selected = c }) { Text(c.optString("name", "دانش‌آموز"), fontWeight = FontWeight.Black); Text(c.optString("subject"), color = Purple700) }
        }; if (contacts.isEmpty() && !loading) item { EmptyState("مخاطبی در محدوده تدریس شما نیست.") } }
        else {
            val c = selected!!; val sid = c.optString("studentId"); val subject = c.optString("subject"); val thread = messages.filter { it.optString("studentId") == sid && it.optString("subject") == subject }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) { items(thread) { m ->
                val mine = m.optString("fromRole") == "teacher"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) { Surface(color = if (mine) Purple700 else Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) { Column(Modifier.padding(12.dp).widthIn(max = 280.dp)) { Text(m.optString("message"), color = if (mine) Color.White else TextPrimary); Text(m.optString("at"), color = if (mine) Color.White.copy(.7f) else TextSecondary, fontSize = 9.sp) } } }
                Spacer(Modifier.height(6.dp))
            } }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("پیام…") }, maxLines = 4)
                IconButton(onClick = { if (draft.isNotBlank()) scope.launch { try { ApiClient.post("/api/academic-messages/p58/send", JSONObject().put("studentId", sid).put("subject", subject).put("message", draft.trim())); draft = ""; reload() } catch (e: Throwable) { error = e.message ?: "خطا" } } }) { Icon(Icons.Default.Send, null, tint = Purple700) }
            }
            TextButton(onClick = { selected = null }, Modifier.fillMaxWidth()) { Text("بازگشت به مخاطبان") }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    val features = listOf(
        MenuFeature("دفتر نمره", Icons.Default.AutoGraph, Screen.Grades, "ثبت مستقیم نمره"),
        MenuFeature("آزمون‌ها", Icons.Default.Assignment, Screen.Exams, "لیست آزمون‌های واقعی"),
        MenuFeature("تمرین‌ها", Icons.Default.QuestionAnswer, Screen.Practice, "مرکز تمرین"),
        MenuFeature("کتابخانه دیجیتال", Icons.Default.Folder, Screen.Library, "جزوه‌های سرور"),
        MenuFeature("اعلان‌ها", Icons.Default.Notifications, Screen.Notifications, "اعلان‌های حساب"),
        MenuFeature("حساب و امنیت", Icons.Default.Security, Screen.Account, "نشست و خروج"),
    )
    Column(Modifier.fillMaxSize()) { ScreenHeader("امکانات بیشتر"); LazyColumn(contentPadding = PaddingValues(16.dp)) { items(features) { f ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onNavigate(f.screen) }, RoundedCornerShape(20.dp), CardDefaults.cardColors(Color.White)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(46.dp), CircleShape, Purple700.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(f.icon, null, tint = Purple700) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(f.title, fontWeight = FontWeight.Black); Text(f.subtitle, color = TextSecondary, fontSize = 12.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = TextSecondary) } }
    }; item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(6.dp)); Text("خروج از حساب") } } } }
}

@Composable
private fun SimpleApiListScreen(title: String, path: String, arrayKey: String) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf("") }
    LaunchedEffect(path) { try { rows = ApiClient.get(path).optJSONArray(arrayKey)?.toObjects().orEmpty() } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } }
    Column(Modifier.fillMaxSize()) { ScreenHeader(title); if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()); if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error); LazyColumn(contentPadding = PaddingValues(16.dp)) { items(rows) { r -> SimpleCard { Text(r.optString("title", r.optString("subject", "مورد")), fontWeight = FontWeight.Black); val sub = listOf(r.optString("subject"), r.optString("teacherName"), r.optString("createdAt")).filter { it.isNotBlank() }.joinToString(" · "); if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 12.sp) } }; if (rows.isEmpty() && !loading && error.isBlank()) item { EmptyState("موردی برای نمایش وجود ندارد.") } } }
}

@Composable
private fun AccountScreen(onLogout: () -> Unit) {
    var me by remember { mutableStateOf<JSONObject?>(null) }; var sessions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { me = ApiClient.get("/api/staff/me").optJSONObject("user"); sessions = ApiClient.get("/api/staff/sessions").optJSONArray("sessions")?.toObjects().orEmpty() } catch (e: Throwable) { error = e.message ?: "خطا" } }
    Column(Modifier.fillMaxSize()) { ScreenHeader("حساب و امنیت"); LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { SimpleCard { Text("${me?.optString("firstName").orEmpty()} ${me?.optString("lastName").orEmpty()}", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("نقش: ${me?.optString("role", "teacher")}", color = TextSecondary) } }
        item { Text("نشست‌های فعال", Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Black) }
        items(sessions) { s -> SimpleCard { Text(s.optString("deviceLabel", s.optString("userAgent", "دستگاه")), fontWeight = FontWeight.Bold); Text(s.optString("lastSeenAt", s.optString("createdAt")), color = TextSecondary, fontSize = 11.sp) } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) { Text("خروج از حساب") } }
    } }
}

@Composable private fun ScreenHeader(title: String) { Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Purple900, Purple700))).statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) { Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) } }
@Composable private fun SimpleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Card(modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 0.dp), RoundedCornerShape(18.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(15.dp), content = content) } }
@Composable private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth().padding(16.dp), RoundedCornerShape(22.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Purple700); Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Black, color = Purple900) }; Spacer(Modifier.height(12.dp)); content() } } }
@Composable private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Text(text, color = TextSecondary, textAlign = TextAlign.Center) } }

private fun JSONArray.toObjects(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }
