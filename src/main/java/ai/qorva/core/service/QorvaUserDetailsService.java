package ai.qorva.core.service;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.UserAccountStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Objects;

@Service
public class QorvaUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Autowired
	public QorvaUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		try {
			// Find user by email
			var user = this.userRepository.findByEmail(email);

			// Check if user was found
			if (Objects.isNull(user)) {
				throw new UsernameNotFoundException("User not found");
			}

			// Convert userDTO into Spring Security User
			return User
					.builder()
						.username(user.getEmail())
						.password(user.getEncryptedPassword())
						.disabled(isUserDisabled(user))
						.authorities(new ArrayList<>())
					.build();
		} catch (AuthenticationException e) {
			throw new UsernameNotFoundException("An error occurred while trying to find the user", e);
		}
	}

	private boolean isUserDisabled(ai.qorva.core.dao.entity.User user) {
		return user.getUserAccountStatus().equals(UserAccountStatus.USER_INACTIVE.getValue())
			|| user.getUserAccountStatus().equals(UserAccountStatus.USER_LOCKED.getValue());
	}
}
