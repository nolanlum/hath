/*

Copyright 2008-2012 E-Hentai.org
http://forums.e-hentai.org/
ehentai@gmail.com

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package org.hath.base;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class Out {
	public static final int DEBUG = 1;
	public static final int INFO = 2;
	public static final int WARNING = 4;
	public static final int ERROR = 8;

	public static final int LOGOUT = DEBUG | INFO | WARNING | ERROR;
	public static final int LOGERR = WARNING | ERROR;
	public static final int OUTPUT = INFO | WARNING | ERROR;
	public static final int VERBOSE = ERROR;

	private static int suppressedOutput;
	private static boolean overridden, writeLogs;
	private static PrintStream def_out, def_err;
	private static OutPrintStream or_out, or_err;
	private static FileWriter logout, logerr;
	private static int logout_count, logerr_count;

	private static SimpleDateFormat sdf;

	private static List<OutListener> outListeners;

	static {
		try {
			Settings.initializeDataDir();
		} catch (java.io.IOException ioe) {
			System.err.println("Could not create data directory. Please check file access permissions and free disk space.");
			System.exit(-1);
		}

		overrideDefaultOutput();
	}

	public static void overrideDefaultOutput() {
		if (overridden) {
			return;
		}

		writeLogs = true;
		overridden = true;
		outListeners = new ArrayList<OutListener>();

		suppressedOutput = 0;

		sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // ISO 8601
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		def_out = System.out;
		def_err = System.err;

		or_out = new OutPrintStream(def_out, "out", INFO);
		or_err = new OutPrintStream(def_err, "ERR", ERROR);
		System.setOut(or_out);
		System.setErr(or_err);

		logout = startLogger(Settings.getOutputLogPath());
		logerr = startLogger(Settings.getErrorLogPath());
	}

	public static void addOutListener(OutListener listener) {
		synchronized (outListeners) {
			if (!outListeners.contains(listener)) {
				outListeners.add(listener);
			}
		}
	}

	public static void removeOutListener(OutListener listener) {
		synchronized (outListeners) {
			outListeners.remove(listener);
		}
	}

	public static void disableLogging() {
		info("Logging ended.");
		writeLogs = false;
		try {
			logout.flush();
		} catch (Exception e) {
		}
	}

	private static FileWriter startLogger(String logfile) {
		// delete existing old logs
		for (int i = 3; i >= 0; i--) {
			(new File(logfile + "." + i)).delete();
		}

		(new File(logfile + ".old")).delete();
		(new File(logfile)).renameTo(new File(logfile + ".old"));

		FileWriter log = null;

		if (logfile != null) {
			if (logfile.length() > 0) {
				try {
					log = new FileWriter(logfile, true);
				} catch (java.io.IOException e) {
					e.printStackTrace();
					System.err.println("Failed to open log file " + logfile);
				}
			}
		}

		if (log != null) {
			Out.info("Started logging to " + logfile);
			log("", log);
			log("*********************************************************", log);
			log(sdf.format(new Date()) + " Logging started", log);
		}

		return log;
	}

	private static boolean stopLogger(FileWriter logger) {
		try {
			logger.close();
		} catch (Exception e) {
			e.printStackTrace(def_err);
			def_err.println("Unable to close file writer handle: Cannot rotate log.");
			return false;
		}

		return true;
	}

	public static void debug(String x) {
		or_out.println(x, "debug", DEBUG);
	}

	public static void info(String x) {
		or_out.println(x, "info", INFO);
	}

	public static void warning(String x) {
		or_out.println(x, "WARN", WARNING);
	}

	public static void error(String x) {
		or_out.println(x, "ERROR", ERROR);
	}

	private static void log(String data, int severity) {
		if (((severity & LOGOUT) > 0) && writeLogs) {
			log(data, logout, false);
			if (++logout_count > 100000) {
				logout_count = 0;
				def_out.println("Rotating output logfile...");
				if (stopLogger(logout)) {
					logout = startLogger(Settings.getOutputLogPath());
					def_out.println("Output logfile rotated.");
				}
			}
		}

		if ((severity & LOGERR) > 0) {
			log(data, logerr, true);
			if (++logerr_count > 100000) {
				logerr_count = 0;
				def_out.println("Rotating error logfile...");
				if (stopLogger(logerr)) {
					logerr = startLogger(Settings.getErrorLogPath());
					def_out.println("Error logfile rotated.");
				}
			}
		}
	}

	private static void log(String data, FileWriter writer) {
		log(data, writer, false);
	}

	private static void log(String data, FileWriter writer, boolean flush) {
		if (writer != null) {
			synchronized (writer) {
				try {
					writer.write(data + "\n");
					if (flush) {
						writer.flush();
					}
				} catch (java.io.IOException ioe) {
					// IMPORTANT: writes to the default System.err to prevent loops
					ioe.printStackTrace(def_err);
				}
			}
		}
	}

	public static String verbose(int severity) {
		if ((severity & VERBOSE) > 0) {
			java.lang.StackTraceElement[] ste = java.lang.Thread.currentThread().getStackTrace();

			int offset = 0;
			while (++offset < ste.length) {
				String s = ste[offset].getClassName();
				if (!s.equals("org.hath.base.Out") && !s.equals("org.hath.base.Out$OutPrintStream") && !s.equals("java.lang.Thread")) {
					break;
				}
			}

			if (offset < ste.length) {
				if (!ste[offset].getClassName().equals("java.lang.Throwable")) {
					return "{" + ste[offset] + "} ";
				} else {
					return "";
				}
			} else {
				return "{Unknown Source}";
			}
		}
		else {
			return "";
		}
	}

	private static class OutPrintStream extends PrintStream {
		private PrintStream ps;
		private String name;
		private int severity;
		@SuppressWarnings("unused")
		private Out out; // XXX unused

		public OutPrintStream(PrintStream ps, String name, int severity) {
			super(ps);
			this.ps = ps;
			this.name = name;
			this.severity = severity;
		}

		@Override
		public void println(String x) {
			println(x, name, severity);
		}

		// XXX unused
		@SuppressWarnings("unused")
		public void println(String x, String name) {
			println(x, name, severity);
		}

		public void println(String x, String name, int severity) {
			if (x == null) {
				return;
			}

			boolean output = (severity & Out.OUTPUT & ~Out.suppressedOutput) > 0;
			boolean log = (severity & (Out.LOGOUT | Out.LOGERR)) > 0;

			if (output || log) {
				synchronized (outListeners) {
					String v = Out.verbose(severity);
					String[] split = x.split("\n");
					for (String s : split) {
						String data = sdf.format(new Date()) + " [" + name + "] " + v + s;

						if (output) {
							ps.println(data);

							for (OutListener listener : outListeners) {
								listener.outputWritten(data);
							}
						}

						if (log) {
							Out.log(data, severity);
						}
					}
				}
			}
		}

		@Override
		public void println(boolean x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(char x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(char[] x) {
			println(new String(x));
		}

		@Override
		public void println(double x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(float x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(int x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(long x) {
			println(String.valueOf(x));
		}

		@Override
		public void println(Object x) {
			println(String.valueOf(x));
		}
	}
}
