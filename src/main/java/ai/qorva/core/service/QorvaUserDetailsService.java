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

import static ai.qorva.core.exception.QorvaErrorCodes.AUTH_USER_LOOKUP_FAILED;

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
				throw new UsernameNotFoundException("error.auth.user_not_found");
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
			throw new UsernameNotFoundException(AUTH_USER_LOOKUP_FAILED, e);
		}
	}

	private boolean isUserDisabled(ai.qorva.core.dao.entity.User user) {
		return user.getUserAccountStatus().equals(UserStatusEnum.INACTIVE.getValue());
	}
}
