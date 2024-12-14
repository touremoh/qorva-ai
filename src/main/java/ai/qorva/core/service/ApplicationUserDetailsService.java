package ai.qorva.core.service;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
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
public class ApplicationUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Autowired
	public ApplicationUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		try {
			// Set up userDTO
			var userToFind = new ai.qorva.core.dao.entity.User();
			userToFind.setEmail(email);

			// Find user by email
			var userFound = this.userRepository.findOneByData(userToFind.getCompanyId(), userToFind);

			// Check if user was found
			if (userFound.isEmpty()) {
				throw new UsernameNotFoundException("User not found");
			}

			// get the found user
			var user = userFound.get();

			// Convert userDTO into Spring Security User
			return User
					.builder()
						.username(user.getEmail())
						.password(user.getEncryptedPassword())
						.disabled(user.getAccountStatus().equals(UserStatusEnum.INACTIVE.getValue()) || user.getAccountStatus().equals(UserStatusEnum.LOCKED.getValue()))
						.accountLocked(isAccountLocked(user))
						.authorities(new ArrayList<>())
					.build();
		} catch (AuthenticationException e) {
			throw new UsernameNotFoundException("An error occurred while trying to find the user", e);
		}
	}

	private boolean isAccountLocked(ai.qorva.core.dao.entity.User user) {
		return user.getAccountStatus().equals(UserStatusEnum.LOCKED.getValue());
	}
}
