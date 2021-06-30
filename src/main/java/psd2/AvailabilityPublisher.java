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


import java.util.Scanner;

public class AvailabilityPublisher extends Analytics
{
    public static void main (final String argv[])
    {
		try
		{
            if (argv.length < 3)
            {
                System.err.println ("Usage: psd2.AvailabilityPublisher");
                System.err.println ("   account");
                System.err.println ("   key");
                System.err.println ("   url");
                return;
            }
            else if (argv.length > 3)
            {
                _debug_ = true;
            }

            final String account = argv[0];
            final String key = argv[1];
            final String url = argv[2];
            log ("account: \"" + account + "\"");
            log ("key: \"" + "***" + "\"");
            log ("url: \"" + url + "\"");
			Analytics analytics = new Analytics ();
            analytics.init ();
			String data = "[";
			String separator = "";

			Scanner scanner = new Scanner (System.in);

			// Skip header
			if (scanner.hasNextLine ())
			{
				scanner.nextLine ();
			}

			// Read body
			while (scanner.hasNextLine ())
			{
				String line = scanner.nextLine ();
				String field [] = line.split (",");

				if (field.length == 2)
				{
					data += separator + "{\"eventTimestamp\":" + field[0] +
						",\"availability\":" + field[1] + "}";
					separator = ",";
				}
			}

			data += "]";
			System.out.println (data);
			analytics.request (account, key, "POST", url, "json", data);
		}
        catch (Exception x)
        {
            x.printStackTrace ();
        }
    }
}
