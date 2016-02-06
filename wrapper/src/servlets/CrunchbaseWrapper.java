package servlets;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import utilities.JSONLDHelper;
import utilities.MappingUtility;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * Servlet implementation class CrunchbaseWrapper
 */
@WebServlet("/api/*")
public class CrunchbaseWrapper extends HttpServlet {
	private static final long serialVersionUID = 1L;
    private static final String CB_API_BASE_URI = "https://api.crunchbase.com/v/3";
    
    
    //local testing
//    private static final String PUBLIC_URL = "http://localhost:8080/CrunchbaseWrapper/";
    // productive use
    private static final String PUBLIC_URL = "http://km.aifb.kit.edu/services/crunchbase/";
    
    private OkHttpClient client;
    private JSONLDHelper jsonldHelper;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public CrunchbaseWrapper() {
        super();
    }

    /**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        MappingUtility mu = (MappingUtility) getServletContext().getAttribute(Listener.MAPPING);
        jsonldHelper = new JSONLDHelper(PUBLIC_URL + "context.jsonld", CB_API_BASE_URI, mu);
        client = new OkHttpClient();
        client.setConnectTimeout(30, TimeUnit.SECONDS); //maybe increase, crunchbase isnt always fast
		
		String endpoint = request.getRequestURI().replaceFirst(request.getContextPath(), ""); // extract api endpoint from query
		endpoint = endpoint.substring(4, endpoint.length()); //remove "/api"
		
		
		String query = endpoint; 
		if (request.getQueryString() != null) {
			query += "?" + request.getQueryString();
		}

		HttpUrl url  = HttpUrl.parse(CB_API_BASE_URI+query);

		// check if user_key was sent by http header. use it for crunchbase, if no other key was set.
		String key = "";
		if (request.getParameterMap().containsKey("user_key")) {
			key = request.getParameterMap().get("user_key")[0];
		} else if ( request.getHeader("user_key") != null) {
			key =  request.getHeader("user_key");
			url = url.newBuilder().addQueryParameter("user_key", key).build();
		}
		
//		System.out.println(url);

//		System.out.println(url.toString());
		//finally build api request for crunchbase
		Request apiRequest = new Request.Builder().url(url).build();
		// get api response
		Response apiResponse = client.newCall(apiRequest).execute();
		
		// set response header of this servlet
		response.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
		response.setStatus(apiResponse.code());
		response.setCharacterEncoding("UTF-8");
		
		String result =  apiResponse.body().string();
		
		String requestAccept = request.getHeader("Accept");
		
		
		if (requestAccept == null) {
			// set default here
			requestAccept = "application/ld+json"; 
		}
		
		if (apiResponse.code() == 200) { //errorhandling
			// content negotiation
			if (requestAccept.contains("application/ld+json") || requestAccept.contains("*/*")) {
				//do jsonld
				response.setContentType("application/ld+json");
				response.getWriter().write(jsonldHelper.json2jsonld(result, endpoint));
			} else if (requestAccept.contains("text/turtle") || requestAccept.contains("application/x-turtle")) {
				// do rdf
				response.setContentType("text/turtle");
				response.getWriter().write(jsonldHelper.json2rdf(result, endpoint));
			} else {
				// just display the json from crunchbase
				response.setContentType("application/json");
				response.getWriter().write(result);
			}
		} else {
			response.getWriter().write(result);
		}
	}
	
	/**
	 * this function generates the baseURI for the RDF transformation from the given endpoint and public URL
	 * @param endpoint
	 * @return
	 */
	private static String getBaseURI(String endpoint) {
		if (endpoint.endsWith("/")) {
			endpoint = endpoint.substring(0, endpoint.lastIndexOf("/"));
		}
		return PUBLIC_URL+"api"+endpoint.substring(0, endpoint.lastIndexOf("/")+1);
	}



}
