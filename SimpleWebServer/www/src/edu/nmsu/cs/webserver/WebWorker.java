package edu.nmsu.cs.webserver;

/**
 * Web worker: an object of this class executes in its own new thread to receive and respond to a
 * single HTTP request. After the constructor the object executes on its "run" method, and leaves
 * when it is done.
 *
 * One WebWorker object is only responsible for one client connection. This code uses Java threads
 * to parallelize the handling of clients: each WebWorker runs in its own thread. This means that
 * you can essentially just think about what is happening on one client at a time, ignoring the fact
 * that the entirety of the webserver execution might be handling other clients, too.
 *
 * This WebWorker class (i.e., an object of this class) is where all the client interaction is done.
 * The "run()" method is the beginning -- think of it as the "main()" for a client interaction. It
 * does three things in a row, invoking three methods in this class: it reads the incoming HTTP
 * request; it writes out an HTTP header to begin its response, and then it writes out some HTML
 * content for the response content. HTTP requests and responses are just lines of text (in a very
 * particular format).
 * 
 * @author Jon Cook, Ph.D.
 *
 **/

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.net.Socket;
import java.text.DateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

public class WebWorker implements Runnable
{

	private Socket socket;
	private int response;
	private String authority, path;
	private StringBuffer sBuffer;
	//private IntBuffer intBuff;
	/**
	 * Constructor: must have a valid open socket
	 **/
	public WebWorker(Socket s)
	{
		socket = s;
	}

	/**
	 * Worker thread starting point. Each worker handles just one HTTP request and then returns, which
	 * destroys the thread. This method assumes that whoever created the worker created it with a
	 * valid open socket object.
	 **/
	public void run()
	{
		System.err.println("Handling connection...");
		try
		{
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			readHTTPRequest(is);
			writeHTTPHeader(os, "text/html");
			writeContent(os, sBuffer);
			os.flush();
			socket.close();
		}
		catch (Exception e)
		{
			System.err.println("Output error: " + e);
		}
		System.err.println("Done handling connection.");
		return;
	}

	/**
	 * Read the HTTP request header.
	 **/
	private void readHTTPRequest(InputStream is)
	{
		String line, request;
		int needPath = 1;
		BufferedReader r = new BufferedReader(new InputStreamReader(is));
		while (true)
		{
			try
			{
				while (!r.ready())
					Thread.sleep(1);
				line = r.readLine();
				if (needPath == 1) {
					request = line;
					authority = request.split(" ")[0];
					// here I obtain the URI to the data file
					// the root is the directory where the webserver was executed
					path = request.split(" ")[1];
					needPath = 0;
				} // end if (needPath...
				System.err.println("Request line: (" + line + ")");
				if (line.length() == 0)
					break;
			}
			catch (Exception e)
			{
				System.err.println("Request error: " + e);
				break;
			}
		}
		return;
	}

	/**
	 * Write the HTTP header lines to the client network connection.
	 * 
	 * @param os
	 *          is the OutputStream object to write to
	 * @param contentType
	 *          is the string MIME content type (e.g. "text/html")
	 **/
	private void writeHTTPHeader(OutputStream os, String contentType) throws Exception
	{
		LocalDate date = LocalDate.now();
		String line = null, type = "";
		//int coolbyte = -1;
		Date d = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
		FileSystem fs = FileSystems.getDefault();
		PathMatcher pMatchFile, pMatchImg;
		
		// here I am getting the file system path that matches the URI
		Path fsPath  = Paths.get(path);
		fsPath = Paths.get("." + fsPath.toString());
		System.out.println(fsPath.toString());
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		sBuffer = new StringBuffer();
		response = checkPath(fsPath);
		
		if (authority.matches("GET") && response == 200) {
			
			pMatchFile = fs.getPathMatcher("glob:**.{html,txt}");
			pMatchImg = fs.getPathMatcher("glob:**.{gif, png, jpeg}");
			
			if (pMatchFile.matches(fsPath)) { 
				
				contentType = Files.probeContentType(fsPath);
				//DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
				try (BufferedReader br = new BufferedReader(new FileReader(fsPath.toString()))) {
					while((line = br.readLine()) != null) {
						line = line.replaceAll("<cs371date>", date.format(formatter));
						line = line.replaceAll("<cs371server>", "Chris Davila's Server");
						sBuffer.append(line);
					}
				} 
				catch (Exception e) {
					response = 404;
					//os.write("HTTP/1.0 404 Not Found\n".getBytes());
				}
				
			} else if (pMatchImg.matches(fsPath)) {
				
				contentType = Files.probeContentType(fsPath);
				
			} // end if-else if
			
		} // end if authority.matches ...
		
		if (response == 404) {
			Path error = Paths.get("C:\\Users\\cdavila\\Documents\\CS_371\\Programs\\SimpleWebServer\\www\\404error.html");
			contentType = Files.probeContentType(error);
			BufferedReader br = new BufferedReader(new FileReader(error.toString()));
			while((line = br.readLine()) != null) {
				line = line.replaceAll("<cs371date>", date.format(formatter));
				line = line.replaceAll("<cs371server>", "Chris Davila's Server");
				sBuffer.append(line);
			}
			os.write("HTTP/1.0 404 Not Found\n".getBytes());
		}
		os.write("HTTP/1.1 200 OK\n".getBytes());
		os.write("Date: ".getBytes());
		os.write((df.format(d)).getBytes());
		os.write("\n".getBytes());
		os.write("Server: Chris's very own server\n".getBytes());
		// os.write("Last-Modified: Wed, 08 Jan 2003 23:11:55 GMT\n".getBytes());
		// os.write("Content-Length: 438\n".getBytes());
		os.write("Connection: close\n".getBytes());
		os.write("Content-Type: ".getBytes());
		os.write(contentType.getBytes());
		os.write("\n\n".getBytes()); // HTTP header ends with 2 newlines
		return;
	}

	/**
	 * Write the data content to the client network connection. This MUST be done after the HTTP
	 * header has been written out.
	 * 
	 * @param os
	 *          is the OutputStream object to write to
	 **/
	private void writeContent(OutputStream os, StringBuffer sb) throws Exception
	{
		int c = 0;
		// here I am getting the file system path that matches the URI
		Path fsPath  = Paths.get(path);
		fsPath = Paths.get("." + fsPath.toString());
		
		// here is where you will output the html code onto the web broswer 
		if (path.contains(".html") || path.contains(".txt")) {
			
			// print the contents of the file
			os.write(sb.toString().getBytes());
			
		} else if (path.contains(".gif") 
				   || path.contains(".png")
				   || path.contains(".jpg")) { 
			
			BufferedInputStream bin = new BufferedInputStream(new FileInputStream(fsPath.toString()));
			while ((c = bin.read()) != -1)
				os.write(c);
			
		} else if (response == 404) {
			
			// print the contents of the 404error file
			os.write(sb.toString().getBytes());
			
		} else {
			
			os.write("<html><head></head><body>\n".getBytes());
			os.write("<h3>My web server works!</h3>\n".getBytes());
			os.write("</body></html>\n".getBytes());
			
		}
	}
	
	private int checkPath(Path in_path) {
		
		if (!new File(in_path.toString()).exists())
			return 404;
		
		return 200;
	} 

} // end class
