package studio.modryn.memento.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import studio.modryn.memento.domain.model.SearchResult
import studio.modryn.memento.domain.model.SearchState
import studio.modryn.memento.ui.theme.MementoColors

/**
 * Main search screen - the "invisible butler" UI.
 * 
 * Design principles:
 * - Minimal: Just a search bar and results
 * - Dark: Black background, soft text
 * - Fast: Instant feedback, no loading spinners for quick searches
 * - Temporary: Designed to appear and disappear quickly
 * 
 * Performance optimizations:
 * - Stable keys for LazyColumn items
 * - Remember expensive operations
 * - Minimal recomposition scope
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    val expandedResultId by viewModel.expandedResultId.collectAsState()
    
    // Remember callbacks to avoid recomposition
    val onQueryChange = remember { viewModel::onQueryChange }
    val onClear = remember { viewModel::clearQuery }
    val onSearch = remember { viewModel::searchNow }
    val onToggleExpanded = remember { viewModel::toggleExpanded }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MementoColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Search bar
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onClear = onClear,
                onSearch = onSearch
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results or status
            when (val state = searchState) {
                is SearchState.Idle -> {
                    IdleState(noteCount = noteCount)
                }
                is SearchState.Loading -> {
                    LoadingState()
                }
                is SearchState.Success -> {
                    ResultsList(
                        results = state.results,
                        expandedResultId = expandedResultId,
                        onResultClick = onToggleExpanded
                    )
                }
                is SearchState.Empty -> {
                    EmptyState(query = state.query)
                }
                is SearchState.Error -> {
                    ErrorState(message = state.message)
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MementoColors.SearchBarBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MementoColors.OnSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MementoColors.OnBackground
            ),
            cursorBrush = SolidColor(MementoColors.Primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "What are you looking for?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MementoColors.OnSurfaceMuted
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        AnimatedVisibility(visible = query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MementoColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IdleState(noteCount: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (noteCount > 0) {
                    "Your memory is ready"
                } else {
                    "Setting up your memory..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MementoColors.OnSurfaceMuted
            )
            
            if (noteCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$noteCount notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MementoColors.OnSurfaceMuted.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MementoColors.Primary,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun ResultsList(
    results: List<SearchResult>,
    expandedResultId: String?,
    onResultClick: (String) -> Unit
) {
    LazyColumn {
        items(
            items = results,
            key = { it.noteId }
        ) { result ->
            SearchResultCard(
                result = result,
                isExpanded = expandedResultId == result.noteId,
                onClick = { onResultClick(result.noteId) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevron_rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(animationSpec = tween(durationMillis = 200)),
        colors = CardDefaults.cardColors(
            containerColor = MementoColors.ResultCardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row with title and expansion indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = result.noteTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MementoColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Expansion indicator
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MementoColors.OnSurfaceMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // File name (muted)
            Text(
                text = result.noteFileName,
                style = MaterialTheme.typography.bodySmall,
                color = MementoColors.OnSurfaceMuted
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Matched snippet with highlighted terms
            Text(
                text = result.matchedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MementoColors.OnSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Match type indicator - friendlier language
                    Row {
                        Text(
                            text = when (result.matchType) {
                                SearchResult.MatchType.SEMANTIC -> "Found by meaning"
                                SearchResult.MatchType.KEYWORD -> "Found by words"
                                SearchResult.MatchType.HYBRID -> "Strong match"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MementoColors.Primary
                        )
                        
                        // Only show score for expanded view, and make it subtle
                        if (result.score >= 0.8f) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Very relevant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MementoColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "I couldn't find anything about that",
                style = MaterialTheme.typography.bodyMedium,
                color = MementoColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try rephrasing your search",
                style = MaterialTheme.typography.bodySmall,
                color = MementoColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.bodyMedium,
                color = MementoColors.Error.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "I'm still learning — try again in a moment",
                style = MaterialTheme.typography.bodySmall,
                color = MementoColors.OnSurfaceMuted
            )
        }
    }
}
