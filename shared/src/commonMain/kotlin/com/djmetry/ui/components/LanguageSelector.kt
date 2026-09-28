package com.djmetry.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.djmetry.i18n.Locale
import com.djmetry.ui.i18n.useI18n

@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier
) {
    val i18n = useI18n()
    
    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(i18n.locale.displayName)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Locale.values().forEach { locale ->
                DropdownMenuItem(
                    text = { Text(locale.displayName) },
                    onClick = {
                        i18n.setLocale(locale)
                        expanded = false
                    }
                )
            }
        }
    }
}

