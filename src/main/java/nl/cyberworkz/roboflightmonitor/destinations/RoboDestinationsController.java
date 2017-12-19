package nl.cyberworkz.roboflightmonitor.destinations;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import nl.cyberworkz.roboflightmonitor.exceptions.BadRequestException;

@RestController
@RequestMapping(value="/destinations")
public class RoboDestinationsController {
	
	@Autowired
	private DestinationsProcesService destinationsProcesService;
	
	@RequestMapping(method=RequestMethod.GET)
	public ResponseEntity<String> processDestinations() throws BadRequestException, UnsupportedEncodingException {
		
		String number = destinationsProcesService.processDestinations();
		
		return new ResponseEntity<String>(number + " destinations processed", HttpStatus.OK);
		
		
	}

}
