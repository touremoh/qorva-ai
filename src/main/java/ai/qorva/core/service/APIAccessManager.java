package ai.qorva.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import static ai.qorva.core.enums.UserAuthoritiesEnum.ALLOWED;
import static java.util.Optional.ofNullable;

@Service("accessManager")
@RequiredArgsConstructor
public class APIAccessManager {

	private final UserService userService;

	public boolean checkActionAllowed(@AuthenticationPrincipal Authentication authentication, String action) {
		// Get user details
		UserDetails userDetails = (UserDetails) authentication.getPrincipal();

		// Get user dto
		var userDto = userService.findOneByEmail(userDetails.getUsername());

		if (ofNullable(userDto.getAuthorities()).isEmpty()) {
			return false;
		}

		// Check user permissions
		return userDto.getAuthorities()
				.stream()
				.anyMatch(up -> up.getAction().equals(action) && up.getPermission().equals(ALLOWED.getValue()));
	}
}
