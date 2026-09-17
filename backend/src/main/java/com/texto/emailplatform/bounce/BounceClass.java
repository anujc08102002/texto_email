package com.texto.emailplatform.bounce;

/**
 * High-level bounce class. Not a 1:1 map of the first SMTP digit.
 */
public enum BounceClass {
    HARD_BOUNCE,
    SOFT_BOUNCE,
    UNKNOWN
}
