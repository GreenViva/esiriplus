package com.esiri.esiriplus.feature.patient.model

/**
 * Tanzania administrative districts used by the service-location picker.
 * Must stay roughly in sync with `admin-panel/src/lib/tzLocations.ts` so that
 * location offers created in the admin panel can actually be matched against
 * client-submitted districts.
 */
data class TzDistrict(
    val region: String,
    val district: String,
    val wards: List<String> = emptyList(),
)

val TZ_DISTRICTS: List<TzDistrict> = listOf(
    TzDistrict(
        region = "Dar es Salaam",
        district = "Ubungo",
        wards = listOf("Kibamba", "Kimara", "Mbezi", "Goba", "Makuburi", "Sinza", "Mburahati", "Manzese"),
    ),
    TzDistrict(
        region = "Dar es Salaam",
        district = "Kinondoni",
        wards = listOf("Kawe", "Msasani", "Mikocheni", "Mwananyamala", "Magomeni", "Kijitonyama"),
    ),
    TzDistrict(
        region = "Dar es Salaam",
        district = "Ilala",
        wards = listOf("Upanga", "Kariakoo", "Buguruni", "Ilala", "Tabata", "Segerea", "Kivukoni"),
    ),
    TzDistrict(
        region = "Dar es Salaam",
        district = "Temeke",
        wards = listOf("Mbagala", "Chamazi", "Kurasini", "Mtoni", "Tandika", "Azimio"),
    ),
    TzDistrict(
        region = "Dar es Salaam",
        district = "Kigamboni",
        wards = listOf("Kigamboni", "Vijibweni", "Kibada", "Tungi", "Mjimwema"),
    ),
    TzDistrict(region = "Dodoma",   district = "Dodoma Urban"),
    TzDistrict(region = "Mwanza",   district = "Nyamagana"),
    TzDistrict(region = "Mwanza",   district = "Ilemela"),
    TzDistrict(region = "Arusha",   district = "Arusha Urban"),
    TzDistrict(region = "Arusha",   district = "Arusha Rural"),
    TzDistrict(region = "Mbeya",    district = "Mbeya Urban"),
    TzDistrict(region = "Tanga",    district = "Tanga Urban"),
    TzDistrict(region = "Zanzibar", district = "Urban West"),
    TzDistrict(region = "Morogoro", district = "Morogoro Urban"),
)
