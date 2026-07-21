package com.walletapp.backend.walletimport.domain;

/** Qué fase completó una importación — determina por dónde continuar al reanudar (research.md #5). */
public enum ImportCursorPhase {
    ACCOUNTS,
    CATEGORIES,
    TRANSACTIONS,
    DONE
}
