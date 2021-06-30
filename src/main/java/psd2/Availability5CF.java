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

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.text.ParseException;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;

public class Availability5CF extends Analytics
{
	protected int _numFailed;
	protected String _requestStart;
	protected String _responseEnd;

	Availability5CF ()
	{
		super ();
		_numFailed = 0;
		_requestStart = "";
		_responseEnd = "";
	}

	protected boolean findSuccessfulTransactions (final String start,
		final String end)
		throws IOException, MalformedURLException, ParseException,
			UnsupportedEncodingException, Exception
	{
		long minEventTimestamp = 0;
		String key = "";
		long duration = convert (end) - convert (start);
		log ("Scan for successful transactions between " + start + " and " +
			end + " (" + duration + "ms)");
		InputStream is = querySuccessfulTransactions (start, end);
		JsonParser parser = Json.createParser (is);

		while (parser.hasNext ())
		{
			Event event = parser.next ();

			if (event == Event.KEY_NAME)
			{
				key = parser.getString ();
				// log (key);
			}
			else if (event == Event.VALUE_NUMBER)
			{
				if (key.equals ("results"))
				{
					minEventTimestamp = parser.getLong ();
					// log ("minEventTimestamp = " + minEventTimestamp);
					log ("Successful transaction ran at " +
						convert (minEventTimestamp));
				}
			}
		}

		parser.close ();
		return minEventTimestamp != 0;
	}

	protected long inspectFailedTransaction (final String eventTimestamp,
		final int responseTime, final String errorCodes,
		final String transactionName)
	{
		long downtime = 0;

		try
		{
			log ("Inspect transaction: \"" + eventTimestamp + "\", " +
				responseTime + ", \"" + errorCodes + "\", \"" +
				transactionName + "\"");
			long eventEndMillis = convert (eventTimestamp) + responseTime;
			String eventEnd = convert (eventEndMillis);

			if (_numFailed == 0)
			{
				_numFailed = 1;
				_requestStart = eventTimestamp;
				_responseEnd = eventEnd;
				log ("Transaction failed count = " + _numFailed);
				log ("Request start = " + _requestStart);
				log ("Response end = " + _responseEnd);
			}
			else	// _numFailed > 0
			{
				long requestStartMillis = convert (_requestStart);
				long responseEndMillis = convert (_responseEnd);
				long duration = responseEndMillis - requestStartMillis;

				if (findSuccessfulTransactions (_requestStart, eventTimestamp))
				{
					if (_numFailed >= 5 && duration > 30 * MILLIS_IN_SECOND)
					{
						// First success after a sequence of at least 5 failures
						log ("Downtime from " + _requestStart + " to " +
							_responseEnd + " [" + _numFailed  + ", " +
							duration + "ms]");
						downtime = duration;
					}
					else
					{
						log ("Downtime criteria not met: [" + _numFailed +
							", " + duration + "ms]");
					}

					// Only the last failed transaction contributes now
					_numFailed = 1;
					_requestStart = eventTimestamp;
					_responseEnd = eventEnd;
					log ("Transaction failed count = " + _numFailed);
				}
				else
				{
					// Set the response end time to the longest event end
					if (responseEndMillis < eventEndMillis)
					{
						responseEndMillis = eventEndMillis;
						_responseEnd = eventEnd;
					}

					_numFailed++;
					duration = responseEndMillis - requestStartMillis;
					log ("No successful transactions found");
					log ("Transaction failed count = " + _numFailed);
					log ("Response end = " + _responseEnd);
					log ("Potential downtime from " + _requestStart + " to " +
						_responseEnd + ": [" + _numFailed + ", " + duration +
						"ms]");
				}
			}
		}
		catch (Exception x)
		{
			// Skip this transaction
			log (x.getMessage ());
		}

		return downtime;
	}

	public double calculate (final String start, final String end)
		throws Exception
	{
		// inspectFailedTransaction should be a nested function
		// and then we wouldn't need these as instance variables...
		// Could create a nested class and let it keep track...
		_numFailed = 0;
		_requestStart = start;
		_responseEnd = start;

		// It would be easier to slurp all the JSON in one go but there may be
		// a large number of records so stream instead in order to reduce memory		// and improve performance.
		InputStream is = queryFailedTransactions (start, end);
		JsonParser parser = Json.createParser (is);
		String key = "";
		int depth = 0;
		int d4String = 0;
		String eventTimestamp = "";
		String transactionName = "";
		int responseTime = 0;
		String errorCodes = "";
		long downtime = 0;
		int n = 0;

		// A finite-state machine might be better...
		while (parser.hasNext ())
		{
			Event event = parser.next ();

			if (event == Event.KEY_NAME)
			{
				key = parser.getString ();
				// log ("[" + depth + "] " + key);
			}
			else if (event == Event.VALUE_NUMBER)
			{
				if (key.equals ("total"))
				{
					int total = parser.getInt ();
					// log ("[" + depth + "] total = " + total);
				}
				else if (key.equals ("results"))
				{
					responseTime = parser.getInt ();
					// log ("[" + depth + "] responseTime = " + responseTime);
				}
			}
			else if (event == Event.VALUE_STRING)
			{
				if (key.equals ("results"))
				{
					if (depth == 4)
					{
						if (d4String % 2 == 0)
						{
							eventTimestamp = parser.getString ();
							// log ("[" + depth + "." + d4String +
							// 	"] eventTimestamp = " + eventTimestamp);
						}
						else if (d4String % 2 == 1)
						{
							transactionName = parser.getString ();
							// log ("[" + depth + "." + d4String +
							// 	"] transactionName = " + transactionName);
						}

						d4String++;
					}
					else if (depth == 5)
					{
						if (errorCodes.length () > 0)
						{
							errorCodes += ",";
						}

						errorCodes += parser.getString ();
						// log ("[" + depth + "] errorCodes = " + errorCodes);
					}
				}
			}
			else if (event == Event.VALUE_TRUE)
			{
				if (key.equals ("moreData"))
				{
					_moreData = true;
				}
			}
			else if (event == Event.START_OBJECT || event == Event.START_ARRAY)
			{
				if (key.equals ("results") && depth == 4)
				{
					errorCodes = "";
				}

				depth++;
			}
			else if (event == Event.END_OBJECT || event == Event.END_ARRAY)
			{
				if (key.equals ("results") && depth == 4)
				{
					downtime += inspectFailedTransaction (eventTimestamp,
						responseTime, errorCodes, transactionName);
					n++;
				}

				depth--;
			}
		}

		parser.close ();
		log ("Processed " + n + " failed transaction(s)");

		// Add remaining downtime
		long duration = convert (_responseEnd) - convert (_requestStart);

		if (_numFailed >= 5 && duration > 30 * MILLIS_IN_SECOND)
		{
			log ("Downtime from " + _requestStart + " to " +
				_responseEnd + " (" + duration + "ms)");
			downtime += duration;
		}

		return percent (start, end, downtime);
	}

	protected void printCsv (final String from, final String to)
		throws Exception
	{
		// Stick to UTC to avoid problems adding days when the clocks go
		// forwards and backwards.
		// final String tz = "Europe/London";
		final String tz = "UTC";
		log ("Scan for failed transactions between " + from + " and " + to +
			" (" + tz + ")");
		long fromMillis = convert (from);
		long toMillis = convert (to);
		long now = System.currentTimeMillis ();
		int n = 0;
		double overallAvailability = 0.0;
		System.out.println ("\"Date\",\"PSD2 Availability\"");

		while (fromMillis < toMillis && fromMillis < now)
		{
			String start = convert (fromMillis);
			fromMillis += MILLIS_IN_DAY;
			String end = convert (fromMillis);
			double availabilityPct = calculate (start, end);
			System.out.println ("\"" + start + "\"," + availabilityPct);
			overallAvailability += availabilityPct;
			n++;
		}

		infill (fromMillis, toMillis);

		if (n > 0)
		{
			overallAvailability /= (double) n;
		}
		else
		{
			overallAvailability = 100.0;
		}

		log ("Overall availability = " + overallAvailability + "%");
	}

    public static void main (final String argv[])
    {
        try
        {
            if (argv.length < 9)
            {
                System.err.println ("Usage: psd2.Availability5CF");
                System.err.println ("   host");
                System.err.println ("   account");
                System.err.println ("   key");
                System.err.println ("   application (comma-separated list)");
                System.err.println ("   tier (comma-separated list)");
                System.err.println ("   transaction (comma-separated list)");
                System.err.println ("   error (comma-separated list)");
                System.err.println ("   from (ISO-format date - inclusive)");
                System.err.println ("   to (ISO-format date - exclusive)");
                return;
            }
            else if (argv.length > 9)
            {
                _debug_ = true;
            }

            final String host = argv[0];
            final String account = argv[1];
            final String key = argv[2];
            final String app = argv[3];
            final String tier = argv[4];
            final String transaction = argv[5];
            final String error = argv[6];
            final String from = argv[7];
            final String to = argv[8];
            log ("host: \"" + host + "\"");
            log ("account: \"" + account + "\"");
            log ("key: \"" + "***" + "\"");
            log ("application: \"" + app + "\"");
            log ("tier: \"" + tier + "\"");
            log ("transaction: \"" + transaction + "\"");
            log ("error: \"" + error + "\"");
            log ("from: \"" + from + "\"");
            log ("to: \"" + to + "\"");
            Availability5CF a = new Availability5CF ();
            a.init (host, account, key, app, tier, transaction, error);
            a.printCsv (from, to);
            log ("More data is " + a.moreData ());
        }
        catch (Exception x)
        {
            x.printStackTrace ();
        }
    }
}
