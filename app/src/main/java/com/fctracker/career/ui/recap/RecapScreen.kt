package com.fctracker.career.ui.recap

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.logic.Recap
import com.fctracker.career.logic.RecapBlock
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.Bullet
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.theme.FcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The season recap: ten pages (fewer when a page has nothing to show), swiped through and shared as images or a PDF. */
@Composable
fun RecapScreen(viewModel: MainViewModel, season: Int?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state, season) { if (season != null) SeasonView.of(state.copy(viewingSeasonYear = season)) else SeasonView.of(state) }
    val built = remember(state, view) { Recap.build(state, view) }
    RecapViewer(
        viewModel = viewModel, title = "Season recap", label = view.label, built = built, pageNames = Recap.PAGE_NAMES,
        excluded = state.recapExcludedPages, onExcluded = { viewModel.setRecapExcluded(it) },
        clubLine = "${state.clubName} · ${view.label}", fileBase = "recap-${view.season}", shareTitle = "Season recap ${view.label}",
        pdfName = "Season recap ${view.label.replace("/", "-")}.pdf", emptyReason = "Nothing to show this season",
        about = listOf(
            "Ten pages, made by itself when a season ends and any time from Season Summary. Swipe between them.",
            "Each page is an image of its own, so they can be shared together (most apps show them as a swipeable set) or as one PDF.",
            "Uses your crest and club colours; the figures come from your matches and the game."
        ),
        aboutTitle = "About the season recap",
        archivedLabel = if (view.isArchived) "${view.label} · ${view.leagueName}" else null,
        onBack = onBack
    )
}

/** The career recap: the season recap's pages for the whole career, shared the same way. */
@Composable
fun CareerRecapScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val built = remember(state) { com.fctracker.career.logic.CareerRecap.build(state) }
    var excluded by remember { mutableStateOf<List<Int>>(emptyList()) }
    val first = (state.historicalSeasons.map { it.year } + state.startYear).minOrNull() ?: state.startYear
    val label = if (first == state.startYear) SeasonView.seasonLabel(first) else "${SeasonView.seasonLabel(first)}–${SeasonView.seasonLabel(state.startYear)}"
    RecapViewer(
        viewModel = viewModel, title = "Career recap", label = label, built = built, pageNames = com.fctracker.career.logic.CareerRecap.PAGE_NAMES,
        excluded = excluded, onExcluded = { excluded = it },
        clubLine = "${state.clubName} · career", fileBase = "career-recap", shareTitle = "Career recap · ${state.clubName}",
        pdfName = "Career recap ${state.clubName}.pdf", emptyReason = "Nothing to show yet",
        about = listOf(
            "Seven pages about the whole career: every season, the trophies, the records, the hall of fame, the best players and the transfers.",
            "Each page is an image of its own, so they can be shared together or as one PDF.",
            "Uses your crest and club colours; the figures come from every season the app holds."
        ),
        aboutTitle = "About the career recap",
        archivedLabel = null,
        onBack = onBack
    )
}

/** Pages drawn as images: swipe through them, share them one by one or together, or save them as a PDF. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecapViewer(
    viewModel: MainViewModel,
    title: String,
    label: String,
    built: List<com.fctracker.career.logic.RecapPage>,
    pageNames: List<String>,
    excluded: List<Int>,
    onExcluded: (List<Int>) -> Unit,
    clubLine: String,
    fileBase: String,
    shareTitle: String,
    pdfName: String,
    emptyReason: String,
    about: List<String>,
    aboutTitle: String,
    archivedLabel: String?,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = FcTheme.colors
    val builtNumbers = built.map { it.number }.toSet()
    val pages = built.filter { it.number !in excluded }.mapIndexed { i, p -> p.copy(number = i + 1) }
    val style = remember(state.primaryColorHex, state.secondaryColorHex) { RecapRenderer.style(context, state.primaryColorHex, state.secondaryColorHex) }
    var assets by remember { mutableStateOf<RecapRenderer.Assets?>(null) }
    var previews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }

    LaunchedEffect(pages, style, clubLine) {
        val a = withContext(Dispatchers.IO) { assets ?: RecapRenderer.loadAssets(context, state, built) }
        val missing = built.flatMap { p -> p.blocks.filterIsInstance<RecapBlock.Person>().map { it.headshotUrl } }.any { it.isNotBlank() && it !in a.headshots }
        val fresh = if (missing) withContext(Dispatchers.IO) { RecapRenderer.loadAssets(context, state, built) } else a
        assets = fresh
        previews = withContext(Dispatchers.Default) { pages.map { RecapRenderer.render(it, pages.size, clubLine, state.clubName, style, fresh, 720) } }
    }

    suspend fun fullSize(): List<Bitmap> = withContext(Dispatchers.Default) {
        val a = assets ?: withContext(Dispatchers.IO) { RecapRenderer.loadAssets(context, state, built) }
        pages.map { RecapRenderer.render(it, pages.size, clubLine, state.clubName, style, a) }
    }

    fun share(only: Int?) {
        if (busy != null || pages.isEmpty()) return
        busy = if (only == null) "Preparing ${pages.size} images…" else "Preparing the image…"
        scope.launch {
            runCatching {
                val images = fullSize()
                val uris = withContext(Dispatchers.IO) {
                    RecapShare.clear(context)
                    images.mapIndexedNotNull { i, b -> if (only == null || only == i) RecapShare.savePng(context, b, "$fileBase-${i + 1}.png") else null }
                }
                RecapShare.shareImages(context, uris, shareTitle)
            }.onFailure { problem = "Could not prepare the images: ${it.message ?: it.javaClass.simpleName}" }
            busy = null
        }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            busy = "Saving the PDF…"
            scope.launch {
                runCatching {
                    val images = fullSize()
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { RecapShare.writePdf(images, it) } ?: throw java.io.IOException("the file could not be opened") }
                }.onSuccess { problem = null }.onFailure { problem = "Could not save the PDF: ${it.message ?: it.javaClass.simpleName}" }
                busy = null
            }
        }
    }

    FcPage(header = {
        if (archivedLabel != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner(archivedLabel, onBack) }
        FcTopBar(title, sub = if (pages.isEmpty()) label else "$label · page ${pager.currentPage + 1} of ${pages.size}", nav = NavIcon.BACK, onNav = onBack)
    }) {
        if (pages.isEmpty()) {
            item { EmptyCard("Every page is switched off. Turn on at least one page below.") }
        } else {
            item {
                HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { i ->
                    val bmp = previews.getOrNull(i)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(28.dp)).background(c.surface)
                            .semantics { contentDescription = "$title page ${i + 1}: ${pages.getOrNull(i)?.title.orEmpty()}" },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                        else CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FcIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page", { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, size = 40.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" }) {
                        pages.indices.forEach { i ->
                            Box(Modifier.width(if (i == pager.currentPage) 18.dp else 7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(if (i == pager.currentPage) c.accent else c.surface2))
                        }
                    }
                    FcIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page", { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.size - 1)) } }, size = 40.dp)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FcButton(busy ?: if (pages.size == 1) "Share the page" else "Share all ${pages.size} pages", { share(null) }, icon = Icons.Default.Share, full = true, enabled = busy == null && previews.isNotEmpty())
                    FieldPair(
                        { m -> FcButton("Save as PDF", { savePdf.launch(pdfName) }, m, kind = ButtonKind.SECONDARY, icon = Icons.Default.Download, enabled = busy == null && previews.isNotEmpty()) },
                        { m -> FcButton("Share this page", { share(pager.currentPage) }, m, kind = ButtonKind.SECONDARY, icon = Icons.Default.Share, enabled = busy == null && previews.isNotEmpty()) }
                    )
                    problem?.let { Muted(it, 12.sp) }
                }
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Pages to include", icon = Icons.Default.List)
                pageNames.forEachIndexed { i, name ->
                    val n = i + 1
                    val has = n in builtNumbers
                    FcSwitchRow("$n. $name", has && n !in excluded, { on -> onExcluded(if (on) excluded - n else excluded + n) },
                        sub = if (!has) emptyReason else null, enabled = has)
                }
                Muted("A page with nothing to show is left out by itself.", 11.sp)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader(aboutTitle, icon = Icons.Default.Info)
                about.forEach { Bullet(it) }
            }
        }
    }
}
