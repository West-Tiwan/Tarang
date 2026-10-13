package com.westtiwan.tarang.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap

@Composable
actual fun platformDynamicScheme(dark: Boolean): ColorScheme? = null

actual fun Image.toComposeBitmap(): ImageBitmap? = runCatching { toBitmap().asComposeImageBitmap() }.getOrNull()

actual fun ImageRequest.Builder.softwareBitmaps(): ImageRequest.Builder = this
