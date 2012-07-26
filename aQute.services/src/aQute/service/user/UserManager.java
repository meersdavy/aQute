package aQute.service.user;

import java.util.*;

public interface UserManager {

	User getAuthenticatedUser(String id, String assertion);

	Iterator<User> getUsers(String filter);

	Iterator<Role> getRoles(String filter);

	void update(User user);

	void deleteUser(String id);

	void update(Role user);

	void deleteRole(String id);

	User getUser(String email) throws Exception;
}
