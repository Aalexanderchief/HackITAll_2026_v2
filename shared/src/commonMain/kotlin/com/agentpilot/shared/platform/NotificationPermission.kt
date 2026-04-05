package com.agentpilot.shared.platform

import androidx.compose.runtime.Composable

/**
 * Requests notification permission if not already granted.
 * Android: prompts for POST_NOTIFICATIONS on API 33+.
 * Desktop: no-op.
 */
@Composable
expect fun NotificationPermissionRequest()
