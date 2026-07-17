package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.domain.RevokedToken;
import com.walletapp.backend.auth.domain.RevokedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    RevokedTokenRepository revokedTokenRepository;

    @Test
    void savesRevokedTokenWithGivenJtiAndExpiry() {
        LogoutUseCase useCase = new LogoutUseCase(revokedTokenRepository);
        Instant expiresAt = Instant.now().plusSeconds(3600);

        useCase.execute("jti-123", expiresAt);

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(captor.capture());
        assertThat(captor.getValue().jti()).isEqualTo("jti-123");
        assertThat(captor.getValue().expiresAt()).isEqualTo(expiresAt);
    }
}
