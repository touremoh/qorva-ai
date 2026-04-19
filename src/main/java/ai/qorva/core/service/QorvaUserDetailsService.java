package ai.qorva.core.service;

import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.UserStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

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
			// Find the user by email
			var user = this.userRepository.findByEmail(email);

			// Check if the user was found
			if (Objects.isNull(user)) {
				throw new UsernameNotFoundException("User not found");
			}

			// Map custom UserAuthority list to Spring GrantedAuthority using "ACTION:PERMISSION" format
			var grantedAuthorities = user.getAuthorities() != null
				? user.getAuthorities().stream()
					.filter(ua -> ua.getAction() != null && ua.getPermission() != null)
					.map(ua -> new SimpleGrantedAuthority(ua.getAction() + ":" + ua.getPermission()))
					.collect(Collectors.toCollection(ArrayList::new))
				: new ArrayList<SimpleGrantedAuthority>();

			// Convert userDTO into Spring Security User
			return User
					.builder()
						.username(user.getEmail())
						.password(user.getEncryptedPassword())
						.disabled(isUserDisabled(user))
				        .accountExpired(user.getUserAccountStatus().equals(UserStatusEnum.DELETED.getValue()))
						.accountLocked(user.getUserAccountStatus().equals(UserStatusEnum.LOCKED.getValue()))
						.authorities(grantedAuthorities)
					.build();
		} catch (AuthenticationException e) {
			throw new UsernameNotFoundException("An error occurred while trying to find the user", e);
		}
	}

	private boolean isUserDisabled(ai.qorva.core.dao.entity.User user) {
		return user.getUserAccountStatus().equals(UserStatusEnum.INACTIVE.getValue());
	}
}
