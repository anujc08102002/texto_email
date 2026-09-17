package com.texto.emailplatform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DkimKeyMaterialTest {

    @Test
    void generatedKeyPairPublicMatchesPrivate() {
        DkimKeyMaterial.Generated keys = DkimKeyMaterial.generate();
        assertThat(DkimKeyMaterial.keyPairMatches(keys.publicKeyPkcs1(), keys.privateKeyPkcs8())).isTrue();
        assertThat(DkimKeyMaterial.keyPairMatches("AAAA", keys.privateKeyPkcs8())).isFalse();
        assertThat(DkimKeyMaterial.publicMatches(keys.publicKeyPkcs1(), DkimKeyMaterial.loadPkcs8(keys.pkcs8Der()))).isTrue();
        assertThat(DkimKeyMaterial.publicKeyFromPkcs1(keys.publicKeyPkcs1())).isNotNull();
        assertThat(keys.toString()).doesNotContain("pkcs8");
        assertThat(keys.toString()).doesNotContain(keys.privateKeyPkcs8());
    }
}
