/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package psd2;

/**
 *
 * @Martin Pinner
 */

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import psd2.util.Config;

class Analytics
{
	protected final static long MILLIS_IN_SECOND = 1000;
	protected final static long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
	protected final static long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;
	protected final static long MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;
	protected static boolean _debug_;

	protected boolean _moreData;
	protected SimpleDateFormat _iso8601;
	protected String _host;
	protected String _account;
	protected String _key;
	protected String _application;
	protected String _tier;
	protected String _transaction;
	protected String _error;

	public void init ()
	{
		_moreData = false;
		_iso8601 = new SimpleDateFormat ("yyyy-MM-dd");
	}

	public void init (String host, String account, String key,
		String application, String tier, String transaction, String error) throws FileNotFoundException
	{
		init ();
		_host = host;
		_account = account;
		_key = key;
		_application = application;
		_tier = tier;
		_transaction = transaction;
		_error = error;
                        //System.out.println(_host + _account + _key + _application + _tier + _transaction + _error);
                
	}

	protected static void log (final String message)
	{
		if (_debug_) System.out.println (message);
	}

	protected long convert (final String t) throws ParseException
	{
		return _iso8601.parse (t).getTime ();
	}

	protected String convert (long t)
	{
		return _iso8601.format (new Date (t));
	}

	protected static String qw (final String list)
	{
		final String comma = ",";
		final String quote = "'";
		String sep = "";
		StringBuilder quotedList = new StringBuilder ();
		final String array [] = list.split (comma,0);

		for (String item: array)
		{
			quotedList.append (sep + quote + item + quote);
			sep = comma;
		}

		// log (list + " converted to " + quotedList.toString ());
		return quotedList.toString ();
	}

	protected static InputStream request (final String account,
		final String key, final String method, final String url,
		final String type, final String data)
		throws IOException, MalformedURLException, UnsupportedEncodingException, NoSuchAlgorithmException, KeyManagementException
	{
		log (url);
		log (data);
		URL u = new URL (url);
		final byte postData [] = data.getBytes ("UTF-8");
                SSLContext sc = SSLContext.getInstance("SSL");
      
		HttpsURLConnection connection =
			(HttpsURLConnection) u.openConnection ();
                //connection.setSSLSocketFactory(sc);
		connection.setDoOutput (true);
		connection.setRequestMethod (method);
		connection.setRequestProperty ("X-Events-API-AccountName", account);
		connection.setRequestProperty ("X-Events-API-Key", key);
		connection.setRequestProperty ("Content-Type",
			"application/vnd.appd.events+" + type + ";v=2");
		connection.setRequestProperty ("Accept",
			"application/vnd.appd.events+json;v=2");
		connection.setRequestProperty ("charset", "utf-8");
		connection.setRequestProperty ("Content-Length",
			Integer.toString (postData.length));
		OutputStream out = connection.getOutputStream ();
		out.write (postData);
		//connection.setDoInput (true);
		return connection.getInputStream ();
	}

	protected InputStream post (final String url, final String type,
		final String data)
		throws IOException, MalformedURLException, UnsupportedEncodingException, NoSuchAlgorithmException, KeyManagementException
	{
		return request (_account, _key, "POST", url, type, data);
	}

	protected InputStream query (final String start, final String end,
		final String data)
		throws IOException, MalformedURLException, UnsupportedEncodingException, NoSuchAlgorithmException, KeyManagementException, KeyManagementException
	{
		return post (_host + "/events/query" + "?start=" + start +
			"&end=" + end + "&limit=10000", "text", data);
	}

	/*
	protected InputStream publish (final String schema, final String data)
		throws IOException, MalformedURLException, UnsupportedEncodingException
	{
		return post (_host + "/events/publish/" + schema, "json", data);
	}
	*/

	protected InputStream queryFailedTransactions (final String start,
		final String end)
		throws Exception
	{
		return query (start, end,
			"SELECT eventTimestamp, " +
				"responseTime, " +
				"segments.errorList.errorCode, " +
				"transactionName " +
			"FROM transactions " +
			"WHERE application IN (" + qw (_application) + ") " +
			"AND segments.tier IN (" + qw (_tier) + ") " +
			"AND transactionName IN (" + qw (_transaction) + ") " +
			"AND segments.errorList.errorCode IN (" + qw (_error) + ") " +
			"AND userExperience = 'ERROR' " +
			"ORDER BY eventTimestamp ASC");
	}

	protected InputStream querySuccessfulTransactions (final String start,
		final String end)
		throws Exception
	{
		return query (start, end,
			"SELECT min(eventTimestamp) AS eventTimestamp " +
			"FROM transactions " +
			"WHERE application IN (" + qw (_application) + ") " +
			"AND segments.tier IN (" + qw (_tier) + ") " +
			"AND transactionName IN (" + qw (_transaction) + ") " +
			"AND (segments.errorList.errorCode NOT IN (" + qw (_error) + ") " +
				"OR userExperience != 'ERROR')");
	}

	protected double calculate (final String start, final String end)
		throws Exception
	{
		// Default implementation.
		return 100.0;
	}

	protected double percent (final String start, final String end,
		long downtime)
		throws ParseException
	{
		double availability = 100.0;
		long duration = convert (end) - convert (start);

		if (duration > 0)
		{
			double durationD = duration;
			double downtimeD = downtime;
			availability *= (durationD - downtimeD) / durationD;
		}

		return availability;
	}

	public boolean moreData ()
	{
		// This will return true if there are more than 10000 failed
		// transactions in a day.
		// If this happens, then need to rework the algorithm to break the time
		// range into chunks.
		return _moreData;
	}

	protected void infill (long from, long to)
	{
		for (long date = from; date < to; date += MILLIS_IN_DAY)
		{
			System.out.println ("\"" + convert (date) + "\"");
		}
	}

	protected void print (final String account, final String key,
		final String method, final String url, final String type,
		final String data)
		throws IOException, MalformedURLException, ParseException,
			UnsupportedEncodingException, NoSuchAlgorithmException, KeyManagementException
	{
		InputStream is = request (account, key, method, url, type, data);
		Scanner scanner = new Scanner (is);

		while (scanner.hasNextLine ())
		{
			String line = scanner.nextLine ();
			System.out.println (line);
		}
	}

	public static void main (final String argv[])
	{
		try
		{
			if (argv.length < 6)
			{
				System.err.println ("Usage: psd2.Analytics");
				System.err.println ("	account");
				System.err.println ("	key");
				System.err.println ("	method");
				System.err.println ("	url");
				System.err.println ("	type");
				System.err.println ("	data");
				return;
			}
			else if (argv.length > 6)
			{
				_debug_ = true;
			}

			final String account = argv[0];
			final String key = argv[1];
			final String method = argv[2];
			final String url = argv[3];
			final String type = argv[4];
			final String data = argv[5];
			log ("account: \"" + account + "\"");
			log ("key: \"" + "***" + "\"");
			log ("method: \"" + method + "\"");
			log ("url: \"" + url + "\"");
			log ("type: \"" + type + "\"");
			log ("data: \"" + data + "\"");
			Analytics analytics = new Analytics ();
			analytics.print (account, key, method, url, type, data);
		}
		catch (Exception x)
		{
			x.printStackTrace ();
		}
	}
}
