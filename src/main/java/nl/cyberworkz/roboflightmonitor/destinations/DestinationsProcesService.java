package nl.cyberworkz.roboflightmonitor.destinations;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.cyberworkz.roboflightmonitor.destinations.domain.Destination;
import nl.cyberworkz.roboflightmonitor.destinations.domain.DestinationsResponse;
import nl.cyberworkz.roboflightmonitor.exceptions.BadRequestException;

@Service
public class DestinationsProcesService {

	private static Logger LOG = LoggerFactory.getLogger(DestinationsProcesService.class);

	@Value("${RFM_APP_KEY}")
	private String apiKey;

	@Value("${RFM_APP_ID}")
	private String apiId;

	@Value("${schiphol.api.baseurl}")
	private String baseUrl;

	@Value("${schiphol.api.resource.destinations}")
	private String destinationsResource;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ObjectMapper mapper;

	@Value("${destinations.table.name}")
	private String tableName;

	private HttpEntity<Object> headersEntity;

	private DynamoDB db;

	private URI baseUri;

	@PostConstruct
	public void init() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("ResourceVersion", "v1");
		headersEntity = new HttpEntity(headers);
		db = new DynamoDB(dynamoDBClient);

	}

	@Autowired
	private AmazonDynamoDB dynamoDBClient;

	public String processDestinations() throws BadRequestException, UnsupportedEncodingException, URISyntaxException {
		List<Destination> destinations = getDestinations();

		saveDestinations(destinations);

		return Integer.toString(destinations.size());
	}

	private void saveDestinations(List<Destination> destinations) {

		for (Destination destination : destinations) {

			Item item = new Item().withPrimaryKey("iata", destination.getIata()).with("city", destination.getCity())
					.with("country", destination.getCountry())
					.with("airportName_EN", StringUtils.hasText(destination.getPublicName().getEnglish())? destination.getPublicName().getEnglish(): "EMPTY")
					.with("airportName_NL", StringUtils.hasText(destination.getPublicName().getDutch())? destination.getPublicName().getDutch():"EMPTY");

			PutItemOutcome outcome = db.getTable(this.tableName).putItem(item);

			LOG.debug(outcome.getPutItemResult().getSdkHttpMetadata().toString());
		}
	}

	private List<Destination> getDestinations() throws BadRequestException, UnsupportedEncodingException, URISyntaxException {
		URI uri = UriComponentsBuilder.fromUriString(baseUrl + destinationsResource).queryParam("app_id", apiId)
				.queryParam("app_key", apiKey).queryParam("sort", "+publicName").build().toUri();

		LOG.debug("URI:" + uri.toString());

		ResponseEntity<String> responseEntity = restTemplate.exchange(uri, HttpMethod.GET, headersEntity, String.class);

		if (responseEntity.getStatusCode().is2xxSuccessful()) {
			List<Destination> destinations = new ArrayList<>();

			destinations = process(responseEntity, destinations);

			return destinations;
		} else {
			LOG.error("request to airport API failed with code " + responseEntity.getStatusCodeValue());
			LOG.error("reason:" + responseEntity.getStatusCode().getReasonPhrase());
			throw new BadRequestException("failed API call with code " + responseEntity.getStatusCodeValue());
		}

	}

	@SuppressWarnings("unchecked")
	private List<Destination> process(ResponseEntity<String> responseEntity, List<Destination> destinations)
			throws UnsupportedEncodingException, URISyntaxException {

		String linksObject = responseEntity.getHeaders().get("link").get(0);
		HashMap<String, String> links = convertTolinks(linksObject);

		convertToDestinations(responseEntity.getBody(), destinations);

		// read next page
		if (links.containsKey("next")) {
			LOG.debug("next uri:" + links.get("next"));
			URI next = new URI(links.get("next"));
			
			URI uri = getPagedUri(next);
			LOG.debug("paged uri:" + uri.toString());

			ResponseEntity<String> nextResponseEntity = restTemplate.exchange(uri, HttpMethod.GET,
					headersEntity, String.class);
			LOG.debug("response date:" + responseEntity.getHeaders().get("date"));
			LOG.debug("destinations size:" + destinations.size());
			process(nextResponseEntity, destinations);
		} else if (links.containsKey("last")){
			// process last link
			URI last = new URI(links.get("last"));
			
			ResponseEntity<String> lastResponseEntity = restTemplate.exchange(getPagedUri(last), HttpMethod.GET,
					headersEntity, String.class);
			destinations = convertToDestinations(lastResponseEntity.getBody(), destinations);

			LOG.debug("last response date:" + responseEntity.getHeaders().get("date"));
		}	

		return destinations;
	}

	private URI getPagedUri(URI uri) {
		List<NameValuePair> params = URLEncodedUtils.parse(uri, "UTF-8");
		String pageValue = "";
		for (NameValuePair param : params) {
			if (param.getName().equals("page")) {
				pageValue = param.getValue();
			}
		}

		return UriComponentsBuilder.fromUriString(baseUrl + destinationsResource).queryParam("app_id", apiId)
				.queryParam("app_key", apiKey).queryParam("sort", "+publicName").queryParam("page", pageValue).build()
				.toUri();
	}

	private HashMap<String, String> convertTolinks(String linksObject) throws UnsupportedEncodingException {
		HashMap<String, String> linkMap = new HashMap<>();

		String[] links = linksObject.split(",");
		for (int i = 0; i < links.length; i++) {

			// get key first
			String[] keyValue = links[i].split(";");
			String keyRaw = keyValue[1].split("=")[1].trim();

			String key = keyRaw.substring(1, keyRaw.length() - 1);

			// get value
			String valueRaw = keyValue[0].trim();
			String value = URLDecoder.decode(valueRaw.substring(1, valueRaw.length() - 1), "UTF-8").replaceAll("\\s", "+");

			linkMap.put(key, value);
		}

		return linkMap;
	}

	private List<Destination> convertToDestinations(String body, List<Destination> destinations) {
		try {
			mapper.readValue(body, DestinationsResponse.class).getDestinations().stream()
					.filter(d -> d.getCity() != null).filter(d -> d.getIata() != null)
					.forEachOrdered(destinations::add);
		} catch (IOException e) {
			LOG.error(e.getMessage());
		}
		return destinations;
	}
}
