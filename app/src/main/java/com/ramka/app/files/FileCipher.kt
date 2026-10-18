package com.ramka.app.files

import java.io.File

interface FileCipher {
    fun encrypt(src: File, dst: File, transferId: String)
    fun decrypt(src: File, dst: File, transferId: String)
    fun verify(src: File, transferId: String)
}