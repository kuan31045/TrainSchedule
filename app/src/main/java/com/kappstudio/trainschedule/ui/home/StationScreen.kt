package com.kappstudio.trainschedule.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kappstudio.trainschedule.R
import com.kappstudio.trainschedule.domain.model.Name
import com.kappstudio.trainschedule.ui.TrainTopAppBar
import com.kappstudio.trainschedule.ui.components.SwapButton
import com.kappstudio.trainschedule.util.bigStations
import com.kappstudio.trainschedule.util.localize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navigateBack: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsState()
    val pathState = viewModel.pathState.collectAsState()

    Scaffold(
        topBar = {
            TrainTopAppBar(
                title = stringResource(R.string.station_title),
                canNavigateBack = true,
                navigateUp = navigateBack,
                actions = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.checked_desc)
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            StationTopLayout(
                selectedType = uiState.value.selectedStationType,
                departureStation = pathState.value.departureStation.name.localize(),
                arrivalStation = pathState.value.arrivalStation.name.localize(),
                onStationButtonClicked = { viewModel.changeSelectedStation(it) },
                onSwapButtonClicked = { viewModel.swapPath() },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                SingleSelectColumn(
                    items = uiState.value.stationsOfCounty.keys.toList(),
                    selected = uiState.value.selectedCounty,
                    onItemClicked = { viewModel.selectCounty(it) },
                    modifier = Modifier.weight(1f)
                )
                SingleSelectColumn(
                    items =
                    uiState.value.stationsOfCounty[uiState.value.selectedCounty]?.map { it.name }
                        ?: emptyList(),
                    bigStation = bigStations,
                    selected = when (uiState.value.selectedStationType) {
                        SelectedType.DEPARTURE -> pathState.value.departureStation.name
                        SelectedType.ARRIVAL -> pathState.value.arrivalStation.name
                    },
                    onItemClicked = { viewModel.selectStation(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StationTopLayout(
    selectedType: SelectedType,
    departureStation: String,
    arrivalStation: String,
    onStationButtonClicked: (SelectedType) -> Unit,
    onSwapButtonClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        StationButton(
            modifier = Modifier.weight(1f),
            isSelected = selectedType == SelectedType.DEPARTURE,
            desc = stringResource(R.string.from),
            station = departureStation,
            onClicked = { onStationButtonClicked(SelectedType.DEPARTURE) }
        )
        SwapButton(onClicked = onSwapButtonClicked)
        StationButton(
            modifier = Modifier.weight(1f),
            isSelected = selectedType == SelectedType.ARRIVAL,
            desc = stringResource(R.string.to),
            station = arrivalStation,
            onClicked = { onStationButtonClicked(SelectedType.ARRIVAL) }
        )
    }

}

@Composable
fun StationButton(
    isSelected: Boolean,
    desc: String,
    station: String,
    modifier: Modifier = Modifier,
    onClicked: () -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = desc,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge
        )
        ElevatedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner_size)),
            onClick = onClicked,
            border = if (isSelected) BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            ) else null,
        ) {
            Text(
                modifier = Modifier.padding(vertical = 4.dp),
                text = station,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

    }
}

@Composable
fun SingleSelectColumn(
    items: List<Name>,
    bigStation: List<String> = emptyList(),
    selected: Name,
    onItemClicked: (Name) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(
            items = items
        ) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClicked(item) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.localize(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.zh in bigStation) MaterialTheme.colorScheme.surfaceTint else MaterialTheme.colorScheme.onBackground
                )
                if (item == selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(id = R.string.checked_desc),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Preview
@Composable
fun StationTopLayoutPreview() {
    StationTopLayout(
        selectedType = SelectedType.ARRIVAL,
        departureStation = "New Taipei",
        arrivalStation = "Kaohsiung",
        onStationButtonClicked = {},
        onSwapButtonClicked = {}
    )
}

@Preview
@Composable
fun SingleSelectColumnPreview() {
    SingleSelectColumn(
        items = listOf(
            Name("Taipei", "臺北"),
            Name("Hsinchu", "新竹"),
            Name("Kaohsiung", "高雄"),
        ),
        selected = Name("Hsinchu", "新竹"),
        onItemClicked = {}
    )
}