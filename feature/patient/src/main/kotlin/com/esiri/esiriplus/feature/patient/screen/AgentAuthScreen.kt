package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.patient.viewmodel.AgentAuthViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val AgentAmber = Color(0xFFF59E0B)
private val AgentOrange = Color(0xFFEF6C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentAuthScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentAuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated()
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Sign In", "Sign Up")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "eSIRIPlus Agent",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        val scrollState = rememberScrollState()
        ScrollIndicatorBox(scrollState = scrollState, modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Agent logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(AgentAmber, AgentOrange)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "e+",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "eSIRIPlus Agents",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.Black,
            )

            Text(
                text = "Earn money by helping patients access healthcare",
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = BrandTeal,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BrandTeal,
                        )
                    }
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            viewModel.clearError()
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) BrandTeal else Color.Black.copy(alpha = 0.6f),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Error message
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }

            // Tab content
            Column(modifier = Modifier.animateContentSize()) {
                when (selectedTab) {
                    0 -> SignInForm(
                        isLoading = uiState.isLoading,
                        onSignIn = viewModel::signIn,
                    )
                    1 -> SignUpForm(
                        isLoading = uiState.isLoading,
                        onSignUp = viewModel::signUp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        } // ScrollIndicatorBox
    }
}

@Composable
private fun SignInForm(
    isLoading: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, tint = BrandTeal)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = BrandTeal)
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onSignIn(email, password)
            },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { onSignIn(email, password) },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "Sign In",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SignUpForm(
    isLoading: Boolean,
    onSignUp: (name: String, mobile: String, email: String, residence: String, password: String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var fullName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var residence by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full Name", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null, tint = BrandTeal)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = mobile,
        onValueChange = { mobile = it },
        label = { Text("Mobile Number", color = Color.Black) },
        supportingText = {
            Text(
                "Use a valid mobile money number — this will be used for payments",
                color = Color(0xFFB45309),
                fontSize = 11.sp,
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Phone, contentDescription = null, tint = BrandTeal)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, tint = BrandTeal)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = residence,
        onValueChange = { residence = it },
        label = { Text("Place of Residence", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Place, contentDescription = null, tint = BrandTeal)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password", color = Color.Black) },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = BrandTeal)
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onSignUp(fullName, mobile, email, residence, password)
            },
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = agentTextFieldColors(),
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { onSignUp(fullName, mobile, email, residence, password) },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "Sign Up",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun agentTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandTeal,
    unfocusedBorderColor = Color.Black.copy(alpha = 0.3f),
    focusedLabelColor = BrandTeal,
    unfocusedLabelColor = Color.Black.copy(alpha = 0.6f),
    cursorColor = BrandTeal,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
)
