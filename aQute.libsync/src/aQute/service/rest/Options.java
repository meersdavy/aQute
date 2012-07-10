package aQute.service.rest;

import javax.servlet.http.*;

import aQute.service.user.*;

public interface Options {
	HttpServletRequest _request();

	HttpServletResponse _response();

	User _user();

	String _host();
}
