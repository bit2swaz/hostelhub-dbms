package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.Role;
import com.hostelhub.support.OracleTestBase;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.Session;
import org.junit.jupiter.api.Test;

/** FR-01. Read-only, so no reseed needed. */
class AuthServiceTest extends OracleTestBase {

    private final AuthService auth = new AuthService(DB);

    @Test
    void fr01_wardenLoginCarriesOwnBlock() throws Exception {
        Session s = auth.login("warden.b@hostelhub.test", SEED_PASSWORD);
        assertThat(s.role()).isEqualTo(Role.WARDEN);
        assertThat(s.fullName()).isEqualTo("Dr. Lakshmi Menon");
        assertThat(s.blockId()).isEqualTo(queryLong("SELECT block_id FROM block WHERE block_name = 'B Block'"));
    }

    @Test
    void fr01_studentLoginHasNoBlock() {
        Session s = auth.login("arjun.nair@hostelhub.test", SEED_PASSWORD);
        assertThat(s.role()).isEqualTo(Role.STUDENT);
        assertThat(s.blockId()).isNull();
    }

    @Test
    void fr01_emailIsCaseInsensitiveAndTrimmed() {
        assertThat(auth.login("  Priya.Sharma@HostelHub.test ", SEED_PASSWORD).fullName()).isEqualTo("Priya Sharma");
    }

    @Test
    void fr01_wrongPasswordAndUnknownEmailGiveTheSameGenericMessage() {
        ServiceTestBase.assertServiceRejects(() -> auth.login("arjun.nair@hostelhub.test", "wrong-pass"),
                "Invalid email or password.");
        ServiceTestBase.assertServiceRejects(() -> auth.login("nobody@hostelhub.test", SEED_PASSWORD),
                "Invalid email or password.");
    }

    @Test
    void fr01_blankInputIsRejectedBeforeTheDatabase() {
        ServiceTestBase.assertServiceRejects(() -> auth.login("", ""), "Enter your email and password.");
    }
}
