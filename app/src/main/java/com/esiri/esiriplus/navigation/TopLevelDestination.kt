package com.esiri.esiriplus.navigation

import com.esiri.esiriplus.feature.admin.navigation.AdminGraph
import com.esiri.esiriplus.feature.auth.navigation.AuthGraph
import com.esiri.esiriplus.feature.doctor.navigation.DoctorGraph
import com.esiri.esiriplus.feature.patient.navigation.PatientGraph

enum class TopLevelDestination(
    val route: Any,
    val label: String,
) {
    AUTH(AuthGraph, "Auth"),
    PATIENT(PatientGraph, "Patient"),
    DOCTOR(DoctorGraph, "Doctor"),
    ADMIN(AdminGraph, "Admin"),
}
