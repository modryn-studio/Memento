package studio.modryn.memento.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    val expandedResultId by viewModel.expandedResultId.collectAsState()
    
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
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                onSearch = viewModel::searchNow
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
                        onResultClick = viewModel::toggleExpanded
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
                    "$noteCount notes in memory"
                } else {
                    "No notes indexed yet"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MementoColors.OnSurfaceMuted
            )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MementoColors.ResultCardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            Text(
                text = result.noteTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MementoColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // File name (muted)
            Text(
                text = result.noteFileName,
                style = MaterialTheme.typography.bodySmall,
                color = MementoColors.OnSurfaceMuted
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Matched snippet
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
                    
                    // Match type indicator
                    Row {
                        Text(
                            text = when (result.matchType) {
                                SearchResult.MatchType.SEMANTIC -> "Semantic match"
                                SearchResult.MatchType.KEYWORD -> "Keyword match"
                                SearchResult.MatchType.HYBRID -> "Semantic + Keyword"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MementoColors.Primary
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Score: ${String.format("%.2f", result.score)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MementoColors.OnSurfaceMuted
                        )
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
        Text(
            text = "I couldn't find anything about \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MementoColors.OnSurfaceVariant
        )
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MementoColors.Error
        )
    }
}
