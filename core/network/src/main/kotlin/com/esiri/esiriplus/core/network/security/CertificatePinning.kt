package com.esiri.esiriplus.core.network.security

import okhttp3.CertificatePinner

object CertificatePinning {

    // Intermediate CA: WE1, Google Trust Services
    // Root CA: GTS Root R4, Google Trust Services LLC
    // Leaf: supabase.co (rotates more frequently, kept as backup)
    private val SUPABASE_PIN_HASHES = arrayOf(
        "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
        "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        "sha256/p/0tEtQuS5nc34qL4c1JS+XqKizBjtowkWbF6Mw0t9A=",
    )

    fun createCertificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add("*.supabase.co", *SUPABASE_PIN_HASHES)
            .build()
}
