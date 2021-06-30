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

import java.text.ParseException;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

public class ErrorRate extends Analytics
{
	protected void printCsv (final String from, final String to)
		throws ParseException
	{
		final String tz = "UTC";
		log ("Scan for error rates between " + from + " and " + to +
			" (" + tz + ")");
		JsonReader reader = Json.createReader (System.in);
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

		long last = convert (from);
		System.out.println ("\"Date\",\"Sum Errors\",\"Sum Calls\"");
		JsonArray results = object.getJsonArray ("results");

		for (int n = 0; n < results.size (); n++)
		{
			JsonArray a = results.getJsonArray (n);
			long timestamp = Long.parseLong (a.getString (0));
			infill (last, timestamp);
			System.out.println ("\"" + convert (timestamp) + "\"," +
				a.getInt (1) + "," + a.getInt (2));
			last = timestamp + MILLIS_IN_DAY;
		}

		infill (last, convert (to));
	}

	public static void main (final String argv[])
	{
		try
		{
			if (argv.length < 2)
			{
				System.err.println ("Usage: psd2.ErrorRate");
				System.err.println ("	from (ISO-format date - inclusive)");
				System.err.println ("	to (ISO-format date - exclusive)");
				return;
			}
			else if (argv.length > 2)
			{
				_debug_ = true;
			}

			final String from = argv[0];
			final String to = argv[1];
			log ("from: \"" + from + "\"");
			log ("to: \"" + to + "\"");
			ErrorRate er = new ErrorRate ();
			er.init ();
			er.printCsv (from, to);
			log ("More data is " + er.moreData ());
		}
		catch (Exception x)
		{
			x.printStackTrace ();
		}
	}
}

