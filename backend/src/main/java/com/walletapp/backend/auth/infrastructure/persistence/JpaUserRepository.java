package com.walletapp.backend.auth.infrastructure.persistence;

import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.auth.domain.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository springDataRepository;

    JpaUserRepository(SpringDataUserRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public User save(User user) {
        UserEntity saved = springDataRepository.save(toEntity(user));
        return toDomain(saved);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return springDataRepository.findByEmail(email.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return springDataRepository.findById(id.value()).map(JpaUserRepository::toDomain);
    }

    private static UserEntity toEntity(User user) {
        return new UserEntity(
                user.id().value(),
                user.email().value(),
                user.passwordHash().value(),
                user.displayName(),
                user.createdAt(),
                user.failedLoginAttempts(),
                user.lockedUntil());
    }

    private static User toDomain(UserEntity entity) {
        return User.reconstitute(
                UserId.of(entity.getId()),
                new Email(entity.getEmail()),
                new PasswordHash(entity.getPasswordHash()),
                entity.getDisplayName(),
                entity.getCreatedAt(),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil());
    }
}
