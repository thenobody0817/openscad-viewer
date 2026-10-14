package com.openscad.viewer.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openscad.viewer.SharedDocument
import com.openscad.viewer.export.StlExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(sharedDocument: StateFlow<SharedDocument?>? = null) {
    val viewModel: WorkspaceViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Shared content takes priority, so this must run before the demo loader.
    val incoming = sharedDocument?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(incoming) {
        incoming?.let { document ->
            viewModel.setSource(document.text, document.name)
            snackbarHostState.showSnackbar("Loaded ${document.name} from share")
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.source.value.isEmpty()) {
            val demo = runCatching {
                context.assets.open("parametric_box.scad").bufferedReader().use { it.readText() }
            }.getOrElse { "// Demo model unavailable\ncube(10, center = true);\n" }
            viewModel.setSource(demo, "parametric_box.scad")
        }
    }

    AppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WorkspaceScreen(viewModel, snackbarHostState)
        }
    }
}

/**
 * Option B shell: the 3D view fills the screen and a draggable panel slides up
 * from the bottom (Parameters / Code / Console).
 */
@Composable
private fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val console by viewModel.console.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()
    val render by viewModel.render.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMenu by remember { mutableStateOf(false) }
    val mesh = (render as? RenderState.Ready)?.mesh
    val exportName = StlExport.fileName(fileName, values["part"])

    fun export(share: Boolean) {
        val current = mesh ?: return
        exportMenu = false
        scope.launch {
            val uri = runCatching {
                withContext(Dispatchers.IO) {
                    if (share) StlExport.writeForSharing(context, current, exportName)
                    else StlExport.exportToDownloads(context, current, exportName)
                }
            }.getOrElse {
                snackbarHostState.showSnackbar("Export failed: ${it.message ?: "unknown error"}")
                return@launch
            }
            if (share) {
                context.startActivity(
                    Intent.createChooser(StlExport.shareIntent(context, uri, exportName), "Share $exportName"),
                )
            } else {
                snackbarHostState.showSnackbar("Exported $exportName to Downloads/OpenSCAD Viewer")
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val minPanel = 150.dp
    val maxPanel = (screenHeight - 200.dp).coerceAtLeast(minPanel)
    var panelHeight by remember { mutableStateOf(340.dp.coerceAtMost(maxPanel)) }

    val tabs = listOf("Parameters", "Code", "Console")
    val visibleParamCount = model.allParameters.size - model.hiddenParameters.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fileName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${model.visibleSections.size} sections · $visibleParamCount parameters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = viewModel::renderNow) {
                        Text("▶  Render")
                    }
                    Box {
                        IconButton(onClick = { exportMenu = true }, enabled = mesh != null) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export STL") },
                                enabled = mesh != null,
                                onClick = { export(false) },
                            )
                            DropdownMenuItem(
                                text = { Text("Share STL…") },
                                enabled = mesh != null,
                                onClick = { export(true) },
                            )
                        }
                    }
                }
            }

            MeshViewport(state = render, modifier = Modifier.weight(1f).fillMaxWidth())

            Surface(
                modifier = Modifier.fillMaxWidth().height(panelHeight),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val delta = with(density) { dragAmount.toDp() }
                                    panelHeight = (panelHeight - delta).coerceIn(minPanel, maxPanel)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                        )
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (selectedTab) {
                            0 -> CustomizerPanel(model = model, values = values, onValueChange = viewModel::setValue)
                            1 -> CodePanel(source = source)
                            else -> ConsolePanel(lines = console)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(12.dp),
        )
    }
}
