package com.sza.streamsplayer.wear.distribution

/**
 * Which edition of the watch app this is. Same shape as the phone's: the interface lives in `src/main`,
 * each variant source set carries the one implementation and the one DI module that binds it. The watch
 * keeps its own copy because the two modules share no code.
 */
interface Distribution {
    /** Stable identifier of the variant: `store` or `noLegal`. */
    val id: String
}
