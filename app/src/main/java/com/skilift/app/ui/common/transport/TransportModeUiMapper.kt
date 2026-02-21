package com.skilift.app.ui.common.transport

import com.skilift.app.domain.model.TransportMode
import com.skilift.app.ui.theme.BikeGreen
import com.skilift.app.ui.theme.FerryTeal
import com.skilift.app.ui.theme.RailPurple
import com.skilift.app.ui.theme.TransitBlue
import com.skilift.app.ui.theme.WalkGray

fun TransportMode.toUi(): TransportModeUi = when (this) {
    TransportMode.BICYCLE -> TransportModeUi(
        color = BikeGreen,
        iconGlyph = "\uD83D\uDEB2",
        label = "Bike",
        contentDescription = "Bicycle"
    )
    TransportMode.BUS -> TransportModeUi(
        color = TransitBlue,
        iconGlyph = "\uD83D\uDE8C",
        label = "Bus",
        contentDescription = "Bus"
    )
    TransportMode.RAIL -> TransportModeUi(
        color = RailPurple,
        iconGlyph = "\uD83D\uDE86",
        label = "Rail",
        contentDescription = "Rail"
    )
    TransportMode.TRAM -> TransportModeUi(
        color = RailPurple,
        iconGlyph = "\uD83D\uDE8A",
        label = "Tram",
        contentDescription = "Tram"
    )
    TransportMode.FERRY -> TransportModeUi(
        color = FerryTeal,
        iconGlyph = "\u26F4",
        label = "Ferry",
        contentDescription = "Ferry"
    )
    TransportMode.WALK -> TransportModeUi(
        color = WalkGray,
        iconGlyph = "\uD83D\uDEB6",
        label = "Walk",
        contentDescription = "Walk"
    )
}
