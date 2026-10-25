package com.ramka.domain.model

/** Константы служебного контакта «Избранное» (заметки самому себе, только локально). */
object ContactConstants {
    /** Фиксированный localId: по нему чат «Избранного» открывается как обычный (chat/{contactId}). */
    const val SELF_CONTACT_ID = "SELF_CONTACT_ID"

    /** Имя по умолчанию; пользователь может переименовать, getOrCreateSelfContact имя не перезаписывает. */
    const val SELF_CONTACT_ALIAS = "Избранное"
}
