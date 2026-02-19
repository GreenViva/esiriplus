package com.esiri.esiriplus.core.network.mock

object MockResponses {

    val SESSION_RESPONSE = """
        {
            "access_token": "mock-access-token-123",
            "refresh_token": "mock-refresh-token-456",
            "expires_at": 1735689600,
            "user": {
                "id": "user-001",
                "full_name": "John Doe",
                "phone": "+254700000000",
                "email": "john@example.com",
                "role": "PATIENT",
                "is_verified": true
            }
        }
    """.trimIndent()

    val CONSULTATION_RESPONSE = """
        {
            "id": "consult-001",
            "patient_id": "user-001",
            "doctor_id": "doctor-001",
            "service_type": "GENERAL_CONSULTATION",
            "status": "PENDING",
            "notes": null,
            "created_at": "2025-01-01T00:00:00Z",
            "updated_at": null
        }
    """.trimIndent()

    val CONSULTATION_LIST_RESPONSE = """
        [
            {
                "id": "consult-001",
                "patient_id": "user-001",
                "doctor_id": "doctor-001",
                "service_type": "GENERAL_CONSULTATION",
                "status": "PENDING",
                "notes": null,
                "created_at": "2025-01-01T00:00:00Z",
                "updated_at": null
            },
            {
                "id": "consult-002",
                "patient_id": "user-001",
                "doctor_id": null,
                "service_type": "FOLLOW_UP",
                "status": "COMPLETED",
                "notes": "Follow up visit",
                "created_at": "2025-01-02T00:00:00Z",
                "updated_at": "2025-01-03T00:00:00Z"
            }
        ]
    """.trimIndent()

    val PAYMENT_RESPONSE = """
        {
            "id": "pay-001",
            "consultation_id": "consult-001",
            "amount": 1500,
            "currency": "KES",
            "status": "COMPLETED",
            "mpesa_receipt_number": "QKJ3B7X9YM",
            "created_at": "2025-01-01T12:00:00Z"
        }
    """.trimIndent()

    val PAYMENT_LIST_RESPONSE = """
        [
            {
                "id": "pay-001",
                "consultation_id": "consult-001",
                "amount": 1500,
                "currency": "KES",
                "status": "COMPLETED",
                "mpesa_receipt_number": "QKJ3B7X9YM",
                "created_at": "2025-01-01T12:00:00Z"
            }
        ]
    """.trimIndent()

    val VIDEO_TOKEN_RESPONSE = """
        {
            "token": "video-token-abc",
            "room_id": "room-123"
        }
    """.trimIndent()

    val USER_RESPONSE = """
        {
            "id": "user-001",
            "full_name": "John Doe",
            "phone": "+254700000000",
            "email": "john@example.com",
            "role": "PATIENT",
            "is_verified": true
        }
    """.trimIndent()

    val TOKEN_REFRESH_RESPONSE = """
        {
            "access_token": "new-access-token",
            "refresh_token": "new-refresh-token",
            "expires_in": 3600
        }
    """.trimIndent()

    val ERROR_RESPONSE_400 = """
        {
            "message": "Bad Request",
            "code": 400,
            "details": "Invalid input"
        }
    """.trimIndent()

    val ERROR_RESPONSE_401 = """
        {
            "message": "Unauthorized",
            "code": 401
        }
    """.trimIndent()

    val ERROR_RESPONSE_404 = """
        {
            "message": "Not Found",
            "code": 404
        }
    """.trimIndent()

    val ERROR_RESPONSE_500 = """
        {
            "message": "Internal Server Error",
            "code": 500
        }
    """.trimIndent()
}
