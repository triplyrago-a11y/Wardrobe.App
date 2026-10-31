package com.example.wardrobe.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.components.AppleInputField

@Composable
fun PasswordRecoveryDialog(
    initialEmail: String,
    cooldownSeconds: Int,
    onDismiss: () -> Unit,
    onSendReset: (String, () -> Unit) -> Unit
) {
    val colors = AppleTheme.colors
    var email by remember { mutableStateOf(initialEmail) }
    var isSubmitted by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(AppleSpacing.Space24)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AppleGlassSurface(
                shape = RoundedCornerShape(AppleSpacing.CardCornerRadius),
                elevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .testTag("password_recovery_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(AppleSpacing.Space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isSubmitted) {
                        Text(
                            text = "Reset Password",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space8))

                        Text(
                            text = "Enter the email associated with your account and we will send instructions to reset your password.",
                            style = AppleTheme.typography.body.copy(
                                fontSize = 14.sp,
                                color = colors.textSecondary,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                        AppleInputField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address",
                            placeholder = "name@example.com",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (email.isNotBlank() && !isSending && cooldownSeconds == 0) {
                                        isSending = true
                                        onSendReset(email) {
                                            isSending = false
                                            isSubmitted = true
                                        }
                                    }
                                }
                            ),
                            testTag = "reset_email_input"
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space12)
                        ) {
                            AppleButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                isPrimary = false,
                                modifier = Modifier.weight(1f)
                            )

                            AppleButton(
                                text = if (cooldownSeconds > 0) "Wait (${cooldownSeconds}s)" else "Send Link",
                                onClick = {
                                    isSending = true
                                    onSendReset(email) {
                                        isSending = false
                                        isSubmitted = true
                                    }
                                },
                                isPrimary = true,
                                enabled = email.isNotBlank() && cooldownSeconds == 0 && !isSending,
                                isLoading = isSending,
                                modifier = Modifier.weight(1f),
                                testTag = "send_reset_link_button"
                            )
                        }
                    } else {
                        // Success Confirmation View (Generic wording to prevent account enumeration)
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = "Check Email",
                            tint = colors.accent,
                            modifier = Modifier.size(54.dp)
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                        Text(
                            text = "Check Your Inbox",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space8))

                        Text(
                            text = "If an account exists for $email, a secure link to reset your password has been dispatched. Please review your spam folder if it doesn't appear shortly.",
                            style = AppleTheme.typography.body.copy(
                                fontSize = 14.sp,
                                color = colors.textSecondary,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                        AppleButton(
                            text = "Done",
                            onClick = onDismiss,
                            isPrimary = true,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "reset_done_button"
                        )
                    }
                }
            }
        }
    }
}
