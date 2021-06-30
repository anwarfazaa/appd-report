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
import java.net.ProtocolException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import psd2.email.*;
import psd2.util.Config;

public class Availability5Fin30s extends Analytics
{
       
        
	protected String errorCodes (final JsonArray tx, int index)
	{
		String errorCodes = "";
		String sep = "";
		JsonArray errors = tx.getJsonArray (index);

		for (int i = 0; i < errors.size (); i++)
		{
			errorCodes += sep + errors.getString (i);
			sep = ",";
		}

		return errorCodes;
	}

	protected void inspectFailedTransaction (final JsonArray tx)
	{
		log ("Inspect transaction: \"" + tx.getString (0) + "\", " +
			tx.getInt (1) + ", \"" + errorCodes (tx, 2) + "\", \"" +
			tx.getString (3) + "\"");
	}

	public double calculate (final String start, final String end)
		throws IOException, ParseException, ProtocolException,
			UnsupportedEncodingException
	{
		// It is easier to slurp all the JSON in one go but there may be
		// a large number of records so streaming may be better to reduce memory
		// and improve performance.
		InputStream is;
            try {
                is = queryFailedTransactions (start, end);
            
		JsonReader reader = Json.createReader (is);
		JsonArray array = reader.readArray ();
		JsonObject object = array.getJsonObject (0);

		// JsonArray fields = object.getJsonArray ("fields");

		// for (int i = 0; i < fields.size (); i++)
		// {
		// 	JsonObject fieldData = fields.getJsonObject (i);
		// 	String label = fieldData.getString ("label");
		// 	log ("label = " + label);
		// }

		// int total = object.getInt ("total");
		// log ("total = " + total);
		_moreData = object.getBoolean ("moreData");

		JsonArray results = object.getJsonArray ("results");
		int numFailed = 4;
		long duration = 0;
		long downtime = 0;
		int n = 3;
		int rt0 = 0;

		// Need at least five failed transactions.
		// Pre-calculate the potential downtime from the first four.
		// Note, this algorithm assigns the transactions in reverse order
		if (results.size () >= 5)
		{
			// Initial duration = t[n] - t[n - 3]
			JsonArray tx3 = results.getJsonArray (n - 3);
			JsonArray tx2 = results.getJsonArray (n - 2);
			JsonArray tx1 = results.getJsonArray (n - 1);
			JsonArray tx0 = results.getJsonArray (n - 0);
			inspectFailedTransaction (tx3);
			inspectFailedTransaction (tx2);
			inspectFailedTransaction (tx1);
			inspectFailedTransaction (tx0);
			long t3 = convert (tx3.getString (0));
			long t0 = convert (tx0.getString (0));
			duration = t0 - t3;
		}

		log ("Duration = " + duration);

		// Loop over remaining failed transactions, starting at the fifth.
		for (n = 4; n < results.size (); n++)
		{
			JsonArray tx4 = results.getJsonArray (n - 4);
			JsonArray tx3 = results.getJsonArray (n - 3);
			JsonArray tx2 = results.getJsonArray (n - 2);
			JsonArray tx1 = results.getJsonArray (n - 1);
			JsonArray tx0 = results.getJsonArray (n - 0);
			inspectFailedTransaction (tx0);
			long t4 = convert (tx4.getString (0));
			long t3 = convert (tx3.getString (0));
			long t2 = convert (tx2.getString (0));
			long t1 = convert (tx1.getString (0));
			long t0 = convert (tx0.getString (0));
			int rt1 = tx1.getInt (1);
			rt0 = tx0.getInt (1);
			log ("t[" + (n - 4) + ".." + (n - 0) + "] = { "
				+ t4 + ", " + t3 + ", " + t2 + ", " + t1 + ", " + t0 + " }");
			// window = t[n] - t[n - 4]
			long window = t0 - t4;
			log ("Window = " + window);

			if (window <= 30 * MILLIS_IN_SECOND)
			{
				// Add the start time; do not include response time yet.
				numFailed++;
				// duration += t[n] - t[n - 1]
				duration += t0 - t1;
			}
			else
			{
				// Latest failure outside time window. Check previous failures.
				if (numFailed >= 5)
				{
					downtime += duration + rt1;
					log ("Downtime = " + downtime);
				}

				numFailed = 4;
				// duration = t[n] - t[n - 3]
				duration = t0 - t3;
			}

			log ("Num failed = " + numFailed);
			log ("Duration = " + duration);
		}

		log ("Processed " + results.size () + " failed transaction(s)");

		// Add remaining downtime
		if (numFailed >= 5)
		{
			downtime += duration + rt0;
		}

		log ("Downtime = " + downtime);
		return percent (start, end, downtime);
                } catch (Exception ex) {
                return 0;
            }
	}

	protected String printCsv (final String from, final String to)
		throws IOException, ParseException
	{
                String availabilityOutput = "";
		// Stick to UTC to avoid problems adding days when the clocks go
		// forwards and backwards.
		// final String tz = "Europe/London";
		final String tz = "UTC";
		log ("Scan for failed transactions between " + from + " and " + to +
			" (" + tz + ")");
                long fromMillis;
		long toMillis;
               
                    fromMillis = convert (from);
                    toMillis = convert (to);
                
		
		long now = System.currentTimeMillis ();
		int n = 0;
		double overallAvailability = 0.0;
		availabilityOutput += "\"Date\",\"PSD2 Availability\"";

		while (fromMillis < toMillis && fromMillis < now)
		{
			String start = convert (fromMillis);
			fromMillis += MILLIS_IN_DAY;
			String end = convert (fromMillis);
			double availabilityPct = calculate (start, end);
			System.out.println ("\"" + start + "\"," + availabilityPct);
                        availabilityOutput += "\"" + start + "\"," + availabilityPct;
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

		availabilityOutput += "Overall availability = " + overallAvailability + "%";
                return availabilityOutput;
	}

    public static void main (final String argv[])
    {
        try
        {
            //loading values from configuration
            Config configuration = new Config();
            final String host = (String) configuration.DataValues.get("host");
            final String account = (String) configuration.DataValues.get("globalAccountName");
            final String key = (String) configuration.DataValues.get("eventsApiKey");
            final String app = (String) configuration.DataValues.get("applicationName");
            final String tier = (String) configuration.DataValues.get("tierName");
            final String transaction = (String) configuration.DataValues.get("businessTransactionsFilter");
            final String error = (String) configuration.DataValues.get("businessTransactionsError");
            final String enableCustomDate = (String) configuration.DataValues.get("autoDate");
            String from = (String) configuration.DataValues.get("startDate");
            String to = (String) configuration.DataValues.get("endDate");
            
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                if (enableCustomDate.equals("true")) {
                    Calendar cal = Calendar.getInstance();
                    to = formatter.format(cal.getTime());
                    cal.add(Calendar.DATE, -1);
                    from = formatter.format(cal.getTime());
                } 
            
            
            SMTPMail mail = new SMTPMail(configuration.DataValues);
            log ("host: \"" + host + "\"");
            log ("account: \"" + account + "\"");
            log ("key: \"" + "***" + "\"");
            log ("application: \"" + app + "\"");
            log ("tier: \"" + tier + "\"");
            log ("transaction: \"" + transaction + "\"");
            log ("error: \"" + error + "\"");
            log ("from: \"" + from + "\"");
            log ("to: \"" + to + "\"");
            
            Availability5Fin30s a = new Availability5Fin30s ();
            a.init (host, account, key, app, tier, transaction, error);
            String availability = a.printCsv (from, to);
            System.out.println(availability);
            
            mail.sendEmail(availability);
            
            log ("More data is " + a.moreData ());
        }
        catch (Exception x)
        {
            x.printStackTrace ();
        }
    }
}
