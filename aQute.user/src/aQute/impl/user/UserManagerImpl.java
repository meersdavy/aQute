package aQute.impl.user;

import java.util.*;

import aQute.bnd.annotation.component.*;
import aQute.service.store.*;
import aQute.service.user.*;

@Component
public class UserManagerImpl implements UserManager {
	public class RoleImpl extends Role {
		public String	_id;
	}

	public class UserImpl extends User {
		public String	_id;
	}

	Store<UserImpl>	users;
	Store<RoleImpl>	roles;

	@Override
	public User getAuthenticatedUser(String id, String assertion) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<User> getUsers(String filter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<Role> getRoles(String filter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void update(User user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteUser(String id) {
		// TODO Auto-generated method stub

	}

	@Override
	public void update(Role user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteRole(String id) {
		// TODO Auto-generated method stub

	}

	@Reference
	void setStore(DB db) throws Exception {
		users = db.getStore(UserImpl.class, "users");
		roles = db.getStore(RoleImpl.class, "roles");
	}

	@Override
	public User getUser(String email) throws Exception {
		return users.find("_id=%s", email).one();
	}

}
