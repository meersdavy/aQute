package aQute.impl.logger;

import java.io.*;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;

import org.osgi.framework.*;
import org.osgi.service.component.*;
import org.osgi.service.log.*;
import org.osgi.util.tracker.*;

import aQute.bnd.annotation.component.*;
import aQute.impl.logger.LoggerImpl.Config;
import aQute.impl.logger.LoggerImpl.Config.LogType;
import aQute.lib.converter.*;
import aQute.service.logger.*;

@Component(servicefactory = true, designateFactory = Config.class)
public class LoggerImpl implements aQute.service.logger.Log {
	static ConcurrentHashMap<Bundle,Handler>	map			= new ConcurrentHashMap<Bundle,Handler>();
	private Bundle								usingBundle;
	private ServiceTracker						tracker;
	private Handler								handler		= new Handler();
	private Config								config;
	private PrintStream							writer;
	private SimpleDateFormat					dateFormat	= new SimpleDateFormat("hhmmss: ");
	private Logger								utilLogger;

	interface Config {
		public enum LogType {
			OSGI, CONSOLE, UTIL, SLF4J
		}

		boolean noStackTraces();

		LogType logType();

		String dateFormat();
	}

	class Handler implements InvocationHandler {
		boolean			on;
		int				level;
		Pattern			filter;
		Set<Class< ? >>	interfaces	= new HashSet<Class< ? >>();

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (!on)
				return null;

			int level = LogService.LOG_ERROR;

			Class< ? > rt = method.getReturnType();
			if (rt == LogMessages.DEBUG.class)
				level = LogService.LOG_DEBUG;
			else if (rt == LogMessages.INFO.class)
				level = LogService.LOG_INFO;
			else if (rt == LogMessages.WARNING.class)
				level = LogService.LOG_WARNING;
			else if (rt == LogMessages.ERROR.class)
				level = LogService.LOG_ERROR;

			if (this.level < level)
				return null;

			aQute.service.logger.Format annotation = method.getAnnotation(aQute.service.logger.Format.class);
			String message;
			if (annotation != null) {
				message = MessageFormat.format(annotation.value(), args);
			} else {
				StringBuilder sb = new StringBuilder(method.getName());
				for (Object arg : args) {
					sb.append(" ");
					sb.append(arg);
				}
				message = sb.toString();
			}

			Throwable t = null;
			ServiceReference s = null;
			int type = 0;

			for (Object arg : args) {
				if (arg instanceof Throwable && t == null) {
					t = (Throwable) arg;
					type++;
					if (!config.noStackTraces()) {
						t.printStackTrace(writer);
					}
				} else if (arg instanceof ServiceReference) {
					s = (ServiceReference) arg;
					type += 2;
				}
			}

			switch (config.logType()) {
				case OSGI :
					LogService logservice = (LogService) tracker.waitForService(200);
					if (logservice != null) {
						switch (type) {
							case 0 :
								logservice.log(level, message);
								break;

							case 1 :
								logservice.log(level, message, t);
								break;
							case 2 :
								logservice.log(s, level, message);
								break;

							case 3 :
								logservice.log(s, level, message, t);
								break;

						}
					}

					// Fall through if no log service

				case CONSOLE :
					String date;
					synchronized (dateFormat) {
						date = dateFormat.format(new Date());
					}
					writer.printf("%s : %s%n", date, message);
					break;

				case UTIL :
					switch (level) {
						case LogService.LOG_DEBUG :
							utilLogger.finer(message);
							break;

						case LogService.LOG_INFO :
							utilLogger.fine(message);
							break;
						case LogService.LOG_WARNING :
							utilLogger.warning(message);
							break;

						default :
							utilLogger.severe(message);
							break;
					}
			}
			return null;
		}
	}

	@Activate
	void activate(ComponentContext ctx) throws Exception {
		try {
			config = Converter.cnv(Config.class, ctx.getProperties());
			usingBundle = ctx.getUsingBundle();
			tracker = new ServiceTracker(usingBundle.getBundleContext(), LogService.class.getName(), null);
			tracker.open();
			map.put(usingBundle, handler);
			if (config.dateFormat() != null)
				dateFormat = new SimpleDateFormat(config.dateFormat());

			if (config.logType() == LogType.UTIL)
				utilLogger = Logger.getLogger(usingBundle.getLocation());

			writer = System.out;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Deactivate
	void deactivate() {
		map.remove(usingBundle);
		tracker.close();
	}

	@Override
	public <T> T logger(Class<T> specification) {
		handler.interfaces.add(specification);
		return (T) Proxy.newProxyInstance(specification.getClassLoader(), new Class< ? >[] {
			specification
		}, handler);
	}

}
