package com.esiri.esiriplus.core.common.util

object SecurityQuestions {

    const val FIRST_PET = "first_pet"
    const val FAVORITE_CITY = "favorite_city"
    const val BIRTH_CITY = "birth_city"
    const val PRIMARY_SCHOOL = "primary_school"
    const val FAVORITE_TEACHER = "favorite_teacher"

    val LABELS: Map<String, String> = mapOf(
        FIRST_PET to "What was your first pet's name?",
        FAVORITE_CITY to "What is your favorite city?",
        BIRTH_CITY to "What city were you born in?",
        PRIMARY_SCHOOL to "What primary school did you attend?",
        FAVORITE_TEACHER to "Who was your favorite teacher?",
    )

    val ALL: List<String> = listOf(
        FIRST_PET,
        FAVORITE_CITY,
        BIRTH_CITY,
        PRIMARY_SCHOOL,
        FAVORITE_TEACHER,
    )
}
