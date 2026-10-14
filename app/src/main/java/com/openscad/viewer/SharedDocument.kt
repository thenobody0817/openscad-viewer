package com.openscad.viewer

/**
 * A document handed to the app by another app — either through the share sheet
 * (`ACTION_SEND`) or by opening a file (`ACTION_VIEW`).
 */
data class SharedDocument(
    val text: String,
    val name: String,
)
