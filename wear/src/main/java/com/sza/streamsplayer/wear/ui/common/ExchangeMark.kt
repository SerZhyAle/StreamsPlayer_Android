package com.sza.streamsplayer.wear.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sza.streamsplayer.wear.R
import com.sza.streamsplayer.wear.ui.theme.WearAppTheme

private val MARK_SIZE = 24.dp

// EXCHANGE-MARK rule 7: clear space of one eighth of the mark's width on every side.
private val MARK_CLEAR_SPACE = 3.dp
private val PLATE_CORNER = 6.dp

/**
 * S0039: the exchange server's mark at 24 dp (`ICON-RENDER` rule 9: 24 to 32 dp on the watch), on its own neutral
 * plate, because a watch screen can stand on an image or the phone's wallpaper (EXCHANGE-MARK rule 7).
 *
 * Drawn with [Image] and never with `Icon`, which tints: the vendored drawable is shown as it is (rules 2 and 6),
 * and it is not mirrored in a right-to-left layout. Decorative - every caller shows the server's name or the failure
 * in words beside it, so TalkBack does not read the picture.
 */
@Composable
fun ExchangeMarkPlate(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(WearAppTheme.colors.exchangeMarkPlate, RoundedCornerShape(PLATE_CORNER))
            .padding(MARK_CLEAR_SPACE),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_exchange_mark_small),
            contentDescription = null,
            modifier = Modifier.size(MARK_SIZE),
        )
    }
}
