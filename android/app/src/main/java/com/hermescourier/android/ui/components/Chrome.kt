
package com.hermescourier.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hermescourier.android.ui.navigation.appRoutes
import com.hermescourier.android.ui.navigation.navigateSingleTopTo

@Composable
fun HermesTopBar(status: String, detail: String) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hermes Courier", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Text(status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(detail)
        }
    }
}

@Composable
fun HermesBottomBar(navController: NavHostController) {
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(modifier = Modifier.height(72.dp)) {
        appRoutes.forEach { route ->
            NavigationBarItem(
                selected = currentRoute == route.route,
                onClick = { navController.navigateSingleTopTo(route.route) },
                icon = { Text(route.icon) },
                label = { Text(route.label) },
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle)
        }
    }
}
