package com.sza.streamsplayer.distribution

/**
 * Which edition of the app this is. The interface lives in `src/main`; each variant source set
 * (`store`, `noLegal`) carries the one implementation and the one DI module that binds it. A capability
 * that an edition withholds is a real class in that edition's source set, never a default here.
 */
interface Distribution {
    /** Stable identifier of the variant: `store` or `noLegal`. */
    val id: String

    /**
     * S0049: the official page this edition is downloaded and updated from, shown on the About page
     * (SETTINGS-NAVIGATION 2.0 section 3.3). Opening About never contacts it; only a tap on its link does.
     */
    val updatesUrl: String
}
