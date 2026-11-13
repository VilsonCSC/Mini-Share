package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.example.ui.components.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit
) {
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember(userName) { mutableStateOf(userName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar with editable user name on top left
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
                    .clickable {
                        tempName = userName
                        isEditingName = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("user_name_chip")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Person,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = userName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = "Edit Name",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (isEditingName) {
            AlertDialog(
                onDismissRequest = { isEditingName = false },
                title = {
                    Text(
                        text = "EDIT NAME",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SurfaceCardBorder,
                            cursorColor = NeonCyan
                        ),
                        modifier = Modifier.testTag("edit_name_input")
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onUserNameChange(tempName)
                            isEditingName = false
                        },
                        modifier = Modifier.testTag("save_name_button")
                    ) {
                        Text("SAVE", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isEditingName = false }) {
                        Text("CANCEL", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCard
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big Share / Send Card
            BigActionCard(
                title = "SEND",
                icon = AppIcons.Send,
                primaryColor = NeonCyan,
                secondaryColor = ElectricBlue,
                testTag = "send_button",
                onClick = onSendClick
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Big Receive Card
            BigActionCard(
                title = "RECEIVE",
                icon = AppIcons.FileDownload,
                primaryColor = ElectricViolet,
                secondaryColor = Color(0xFF6D28D9),
                testTag = "receive_button",
                onClick = onReceiveClick
            )
        }
    }
}

@Composable
private fun BigActionCard(
    title: String,
    icon: ImageVector,
    primaryColor: Color,
    secondaryColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = primaryColor.copy(alpha = 0.2f), spotColor = primaryColor.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.16f),
                        secondaryColor.copy(alpha = 0.08f),
                        SurfaceCard
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.6f),
                        secondaryColor.copy(alpha = 0.2f),
                        SurfaceCardBorder
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = primaryColor),
                onClick = onClick
            )
            .testTag(testTag)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                secondaryColor.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(
                        2.dp,
                        Brush.sweepGradient(
                            listOf(primaryColor, secondaryColor, primaryColor)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = primaryColor,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
        }
    }
}
