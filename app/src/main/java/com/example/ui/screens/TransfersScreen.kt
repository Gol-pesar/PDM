package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DownloadEntity
import com.example.data.model.SortOption
import com.example.data.model.StatusFilter
import com.example.ui.DownloadQueueUiState
import com.example.ui.MainUiState
import com.example.ui.components.ActiveTransferBanner
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.categoryColorFor
import com.example.ui.components.categoryIconFor

@Composable
fun TransfersScreen(
    uiState: MainUiState,
    queueUiState: DownloadQueueUiState,
    isSearchOpen: Boolean,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onTogglePauseResume: (DownloadEntity) -> Unit,
    onInspectDownload: (DownloadEntity) -> Unit,
    onOpenFile: (DownloadEntity) -> Unit,
    onAddNewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val categoryMap = remember(uiState.categories) {
        uiState.categories.associateBy { it.id }
    }
    val selectedCategory = uiState.filterState.selectedCategoryId?.let { categoryMap[it] }
    val queueSuccess = queueUiState as? DownloadQueueUiState.Success

    val primaryFilters = listOf(
        StatusFilter.ALL,
        StatusFilter.ACTIVE,
        StatusFilter.PAUSED,
        StatusFilter.COMPLETED
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Collapsible Search Bar (only shown when user taps Search icon)
        item(key = "search_box") {
            AnimatedVisibility(visible = isSearchOpen) {
                OutlinedTextField(
                    value = uiState.filterState.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .testTag("search_downloads_input"),
                    placeholder = { Text("جستجو در نام فایل‌ها...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }

        // Sleek Active Downloads Banner (only visible while downloading)
        if (uiState.activeCount > 0) {
            item(key = "active_banner") {
                ActiveTransferBanner(
                    activeCount = uiState.activeCount,
                    aggregateSpeedBps = uiState.aggregateSpeedBps,
                    onPauseAll = onPauseAll
                )
            }
        }

        // Minimal Filter Pills + Sort Icon
        if (uiState.allDownloads.isNotEmpty() || selectedCategory != null) {
            item(key = "filter_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        primaryFilters.forEach { filter ->
                            val selected = uiState.filterState.statusFilter == filter
                            val count = when (filter) {
                                StatusFilter.ALL -> queueSuccess?.totalCount ?: uiState.allDownloads.size
                                StatusFilter.ACTIVE -> queueSuccess?.activeCount ?: (uiState.activeCount + uiState.queuedCount)
                                StatusFilter.PAUSED -> queueSuccess?.pausedCount ?: 0
                                StatusFilter.COMPLETED -> queueSuccess?.finishedCount ?: uiState.completedCount
                                else -> 0
                            }
                            val labelText = if (count > 0 && filter != StatusFilter.ALL) {
                                "${filter.labelFa} ($count)"
                            } else {
                                filter.labelFa
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onStatusFilterChange(filter) },
                                label = {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.testTag("status_chip_${filter.name.lowercase()}")
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("sort_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "مرتب‌سازی",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.labelFa) },
                                    trailingIcon = {
                                        if (uiState.filterState.sortOption == option) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSortChange(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                            if (uiState.allDownloads.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("ادامه همه دانلودها") },
                                    onClick = {
                                        onResumeAll()
                                        sortMenuExpanded = false
                                    }
                                )
                                if (uiState.completedCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("پاک کردن تکمیل‌شده‌ها") },
                                        onClick = {
                                            onClearCompleted()
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Category Chip (if user drilled down from Categories tab)
        if (selectedCategory != null) {
            item(key = "selected_category_chip") {
                val catColor = categoryColorFor(selectedCategory.iconKey)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InputChip(
                        selected = true,
                        onClick = { onCategoryFilterChange(null) },
                        label = { Text("دسته: ${selectedCategory.nameFa}") },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIconFor(selectedCategory.iconKey),
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "حذف فیلتر دسته",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // Downloads List or Friendly Minimal Empty State
        if (uiState.filteredDownloads.isEmpty()) {
            item(key = "empty_state") {
                FriendlyEmptyState(
                    hasAnyDownloads = uiState.allDownloads.isNotEmpty(),
                    onResetFilters = {
                        onSearchChange("")
                        onStatusFilterChange(StatusFilter.ALL)
                        onCategoryFilterChange(null)
                    },
                    onAddNewClick = onAddNewClick
                )
            }
        } else {
            items(uiState.filteredDownloads, key = { it.id }) { download ->
                val cat = categoryMap[download.categoryId]
                DownloadItemCard(
                    download = download,
                    categoryName = cat?.nameFa ?: "سایر",
                    categoryIconKey = cat?.iconKey ?: "other",
                    telemetry = uiState.telemetry[download.id],
                    onTogglePauseResume = { onTogglePauseResume(download) },
                    onOpenOrInspect = { onInspectDownload(download) },
                    onOpenFile = { onOpenFile(download) }
                )
            }
        }
    }
}

@Composable
private fun FriendlyEmptyState(
    hasAnyDownloads: Boolean,
    onResetFilters: () -> Unit,
    onAddNewClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (hasAnyDownloads) {
                    "فایلی توی این بخش نیست!"
                } else {
                    "هنوز فایلی دانلود نکردی!"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasAnyDownloads) {
                    "برای دیدن بقیه فایل‌ها، فیلتر رو روی «همه» بگذار."
                } else {
                    "لینک هر فایلی رو کپی کن و دکمه پایین رو بزن تا با سرعت بالا و منظم برات دانلود بشه."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            if (hasAnyDownloads) {
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("reset_filters_button")
                ) {
                    Text("نمایش همه دانلودها")
                }
            } else {
                Button(
                    onClick = onAddNewClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("empty_add_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن لینک جدید", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
