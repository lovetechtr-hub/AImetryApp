package com.aimetry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aimetry.domain.model.LoginType
import com.aimetry.domain.model.OAuthProvider
import com.aimetry.i18n.Strings
import com.aimetry.ui.i18n.useI18n
import com.aimetry.ui.theme.*

@Composable
fun LoginScreen(
    loginType: LoginType,
    onOAuthClick: (OAuthProvider) -> Unit,
    onEmailLogin: (String, String) -> Unit,
    onRegister: (String, String, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AimetryColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (loginType is LoginType.Artist) i18n.t(Strings.LOGIN_FOR_ARTIST) else i18n.t(Strings.LOGIN_TITLE),
                style = MaterialTheme.typography.headlineMedium,
                color = AimetryColors.Text,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // OAuth кнопки
            OAuthButton(
                provider = OAuthProvider.SPOTIFY,
                onClick = { onOAuthClick(OAuthProvider.SPOTIFY) },
                modifier = Modifier.fillMaxWidth()
            )
            
            OAuthButton(
                provider = OAuthProvider.GOOGLE,
                onClick = { onOAuthClick(OAuthProvider.GOOGLE) },
                modifier = Modifier.fillMaxWidth()
            )
            
            OAuthButton(
                provider = OAuthProvider.APPLE,
                onClick = { onOAuthClick(OAuthProvider.APPLE) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Divider(color = AimetryColors.Border)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Email/Password форма
            if (isRegisterMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(i18n.t(Strings.DISPLAY_NAME)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
            }
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(i18n.t(Strings.EMAIL)) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(i18n.t(Strings.PASSWORD)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors()
            )
            
            Button(
                onClick = {
                    if (isRegisterMode) {
                        onRegister(email, password, displayName.ifBlank { null })
                    } else {
                        onEmailLogin(email, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AimetryColors.Panel
                )
            ) {
                Text(
                    text = if (isRegisterMode) i18n.t(Strings.REGISTER) else i18n.t(Strings.LOGIN),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            TextButton(
                onClick = { isRegisterMode = !isRegisterMode }
            ) {
                Text(
                    text = if (isRegisterMode) i18n.t(Strings.ALREADY_HAVE_ACCOUNT) else i18n.t(Strings.NO_ACCOUNT),
                    color = AimetryColors.Accent
                )
            }
            
            TextButton(onClick = onBack) {
                Text(i18n.t(Strings.BACK), color = AimetryColors.Muted)
            }
        }
    }
}

@Composable
fun OAuthButton(
    provider: OAuthProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    val text = when (provider) {
        OAuthProvider.SPOTIFY -> i18n.t(Strings.LOGIN_WITH_SPOTIFY)
        OAuthProvider.GOOGLE -> i18n.t(Strings.LOGIN_WITH_GOOGLE)
        OAuthProvider.FACEBOOK -> i18n.t(Strings.LOGIN_WITH_FACEBOOK)
        OAuthProvider.APPLE -> i18n.t(Strings.LOGIN_WITH_APPLE)
    }
    
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = if (provider == OAuthProvider.SPOTIFY) {
            ButtonDefaults.buttonColors(
                containerColor = AimetryColors.SpotifyGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = AimetryColors.Panel,
                contentColor = AimetryColors.Text
            )
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AimetryColors.Text,
    unfocusedTextColor = AimetryColors.Text,
    focusedBorderColor = AimetryColors.Accent,
    unfocusedBorderColor = AimetryColors.Border,
    focusedLabelColor = AimetryColors.Accent,
    unfocusedLabelColor = AimetryColors.Muted
)

