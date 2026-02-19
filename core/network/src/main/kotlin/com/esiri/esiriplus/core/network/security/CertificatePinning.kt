package com.esiri.esiriplus.core.network.security

import okhttp3.CertificatePinner

object CertificatePinning {

    // TODO: Replace with actual SHA-256 pin hashes from your Supabase project before deployment.
    // Use: openssl s_client -servername <host> -connect <host>:443 | openssl x509 -pubkey -noout |
    //      openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
    private val SUPABASE_PIN_HASHES = arrayOf(
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
    )

    fun createCertificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("*.supabase.co", *SUPABASE_PIN_HASHES)
            .build()
}
