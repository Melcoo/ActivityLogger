package com.mihai.logger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Data Model
data class ActivityItem(val name: String, val icon: ImageVector, val color: Color)

// Configuration List
val myActivities = listOf(
    // 1. Matei (Parenting) - Primary Blue
    ActivityItem("Matei", Icons.Default.ChildCare, Color(0xFF2979FF)),

    // 2. Food (Eating/Cooking) - Orange
    ActivityItem("Food", Icons.Default.Restaurant, Color(0xFFFF9100)),

    // 3. NQ Live (Active Trading) - Neon Green
    ActivityItem("Trading", Icons.AutoMirrored.Filled.ShowChart, Color(0xFF00E676)),

    // 5. Money Management (Finance/Admin) - Gold
    ActivityItem("Money Mgmt", Icons.Default.AttachMoney, Color(0xFFFFD740)),

    // 6. Shopping - Purple
    ActivityItem("Shopping", Icons.Default.ShoppingCart, Color(0xFFD500F9)),

    // 7. Housework (Chores) - Brown
    ActivityItem("Housework", Icons.Default.Home, Color(0xFF8D6E63)),

    // 8. Outside Stuff (Errands/Transit) - Light Grey
    ActivityItem("Outside Stuff", Icons.Default.DirectionsCar, Color(0xFFE0E0E0)),

    // 8. Outside Stuff (Errands/Transit) - Light Grey
    ActivityItem("Moto", Icons.Default.Motorcycle, Color(0xFFE0E0E0)),
)